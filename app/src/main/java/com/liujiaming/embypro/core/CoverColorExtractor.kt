package com.liujiaming.embypro

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Extracts a reusable color scheme from cover art for immersive media detail pages.
 *
 * The extractor intentionally avoids simple average colors. Instead it samples the lower-middle
 * cover region, filters out washed or muddy pixels, and uses a small K-Means pass to find the
 * most visually dominant vivid color near the transition area. The resulting scheme is cached by
 * image key so repeat openings do not recompute colors on the main thread.
 */
data class CoverColorScheme(
    val imageKey: String,
    val mainColor: Int,
    val surfaceColor: Int,
    val overlayColor: Int,
    val textPrimaryColor: Int,
    val textSecondaryColor: Int,
    val buttonColor: Int,
    val buttonContentColor: Int,
    val heroFogColor: Int,
    val cardColor: Int,
    val cardStrokeColor: Int
)

object CoverColorExtractor {
    private const val CACHE_SIZE = 64
    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val evaluator = android.animation.ArgbEvaluator()
    private val cache = object : LruCache<String, CoverColorScheme>(CACHE_SIZE) {}

    fun shutdown() {
        executor.shutdown()
    }

    fun defaultScheme(imageKey: String = ""): CoverColorScheme {
        val surface = Color.parseColor("#EAF3F6")
        val textPrimary = Color.parseColor("#1F2430")
        val main = Color.parseColor("#147A8B")
        return CoverColorScheme(
            imageKey = imageKey,
            mainColor = main,
            surfaceColor = surface,
            overlayColor = Color.parseColor("#5E96A2"),
            textPrimaryColor = textPrimary,
            textSecondaryColor = ColorUtils.setAlphaComponent(textPrimary, 178),
            buttonColor = Color.parseColor("#166C83"),
            buttonContentColor = Color.WHITE,
            heroFogColor = Color.parseColor("#1F000000"),
            cardColor = Color.parseColor("#DDF4FAFC"),
            cardStrokeColor = Color.parseColor("#4D7AA8B1")
        )
    }

    fun extractColors(imageKey: String, bitmap: Bitmap, onResult: (CoverColorScheme) -> Unit) {
        synchronized(cache) {
            val cached = cache.get(imageKey)
            if (cached != null) {
                onResult(cached)
                return
            }
        }
        executor.execute {
            val scheme = extractColors(bitmap).copy(imageKey = imageKey)
            synchronized(cache) {
                cache.put(imageKey, scheme)
            }
            mainHandler.post { onResult(scheme) }
        }
    }

    fun extractColors(bitmap: Bitmap): CoverColorScheme {
        val sampledBitmap = buildSampleBitmap(bitmap)
        val samples = collectSamples(sampledBitmap)
        if (samples.isEmpty()) {
            return buildScheme(resolvePaletteFallback(sampledBitmap) ?: defaultScheme().mainColor, 0.62f)
        }

        val mainColor = resolveMainColor(samples, sampledBitmap) ?: defaultScheme().mainColor
        val averageValue = samples.sumOf { it.v.toDouble() }.toFloat() / samples.size.toFloat()
        return buildScheme(mainColor, averageValue)
    }

    private fun buildSampleBitmap(bitmap: Bitmap): Bitmap {
        if (bitmap.isRecycled || bitmap.width <= 1 || bitmap.height <= 1) return bitmap
        val left = (bitmap.width * 0.05f).toInt().coerceIn(0, bitmap.width - 1)
        val right = (bitmap.width * 0.95f).toInt().coerceIn(left + 1, bitmap.width)
        val top = (bitmap.height * 0.45f).toInt().coerceIn(0, bitmap.height - 1)
        val bottom = (bitmap.height * 0.90f).toInt().coerceIn(top + 1, bitmap.height)
        val cropWidth = (right - left).coerceAtLeast(1)
        val cropHeight = (bottom - top).coerceAtLeast(1)
        val cropped = runCatching {
            Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
        }.getOrElse { bitmap }
        val targetWidth = cropped.width.coerceIn(80, 140)
        val targetHeight = max(1, (cropped.height * (targetWidth / cropped.width.toFloat())).toInt())
        return Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
    }

    private fun collectSamples(bitmap: Bitmap): List<PixelSample> {
        val hsv = FloatArray(3)
        val samples = ArrayList<PixelSample>(bitmap.width * bitmap.height / 4)
        val step = when {
            bitmap.width >= 120 -> 2
            bitmap.width >= 90 -> 2
            else -> 1
        }

        for (y in 0 until bitmap.height step step) {
            val localY = if (bitmap.height <= 1) 0f else y.toFloat() / (bitmap.height - 1).toFloat()
            for (x in 0 until bitmap.width step step) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) < 255) continue
                Color.colorToHSV(color, hsv)
                val saturation = hsv[1]
                val value = hsv[2]
                if (value < 0.18f) continue
                if (value > 0.95f && saturation < 0.15f) continue
                if (saturation < 0.12f) continue
                samples += PixelSample(
                    color = color,
                    h = hsv[0],
                    s = saturation,
                    v = value,
                    bottomWeight = ((localY - 0.35f) / 0.65f).coerceIn(0f, 1f)
                )
            }
        }
        return samples
    }

    private fun resolveMainColor(samples: List<PixelSample>, bitmap: Bitmap): Int? {
        val clusters = runKMeans(samples, clusterCount = min(5, max(4, samples.size / 80)))
        val bestCluster = clusters.maxByOrNull { cluster ->
            val areaWeight = cluster.count.toFloat() / samples.size.toFloat()
            val saturationWeight = cluster.avgSaturation.coerceIn(0f, 1f)
            val bottomRegionWeight = cluster.avgBottomWeight.coerceIn(0f, 1f)
            areaWeight * 0.45f + saturationWeight * 0.35f + bottomRegionWeight * 0.20f
        }
        val clusterColor = bestCluster?.averageColor
        return clusterColor ?: resolvePaletteFallback(bitmap)
    }

    private fun runKMeans(samples: List<PixelSample>, clusterCount: Int): List<ColorCluster> {
        if (samples.isEmpty()) return emptyList()
        val centers = initializeCenters(samples, clusterCount)
        repeat(5) {
            centers.forEach { it.clear() }
            samples.forEach { sample ->
                val nearest = centers.minByOrNull { center -> colorDistance(sample, center) } ?: return@forEach
                nearest.add(sample)
            }
            centers.forEach { it.recalculate() }
        }
        return centers.filter { it.count > 0 }.map { it.toColorCluster() }
    }

    private fun initializeCenters(samples: List<PixelSample>, clusterCount: Int): List<KMeansCenter> {
        val sorted = samples.sortedByDescending { it.s * 0.7f + it.bottomWeight * 0.3f }
        val picked = ArrayList<PixelSample>(clusterCount)
        for (sample in sorted) {
            if (picked.none { hueDistance(sample.h, it.h) < 18f && abs(sample.v - it.v) < 0.15f }) {
                picked += sample
            }
            if (picked.size == clusterCount) break
        }
        while (picked.size < clusterCount) {
            picked += sorted[picked.size % sorted.size]
        }
        return picked.map { KMeansCenter(it.h, it.s, it.v) }
    }

    private fun colorDistance(sample: PixelSample, center: KMeansCenter): Float {
        val hue = hueDistance(sample.h, center.h) / 180f
        val sat = abs(sample.s - center.s)
        val value = abs(sample.v - center.v)
        return hue * 0.50f + sat * 0.32f + value * 0.18f
    }

    private fun resolvePaletteFallback(bitmap: Bitmap): Int? {
        if (bitmap.isRecycled) return null
        val palette = Palette.from(bitmap).maximumColorCount(12).generate()
        val swatch = palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.dominantSwatch
        return swatch?.rgb
    }

    private fun buildScheme(mainColorSeed: Int, averageValue: Float): CoverColorScheme {
        val correctedMain = correctMainColor(mainColorSeed)
        val surfaceColor = createSurfaceColor(correctedMain)
        val overlayColor = createOverlayColor(correctedMain)
        val buttonColor = createButtonColor(correctedMain)
        val textPrimaryColor = if (ColorUtils.calculateLuminance(surfaceColor) > 0.72) {
            Color.parseColor("#1F2430")
        } else {
            Color.WHITE
        }
        val textSecondaryColor = ColorUtils.setAlphaComponent(
            textPrimaryColor,
            if (textPrimaryColor == Color.WHITE) 190 else 176
        )
        val buttonContentColor = if (ColorUtils.calculateLuminance(buttonColor) > 0.46) {
            Color.parseColor("#10212B")
        } else {
            Color.WHITE
        }
        val heroFogColor = when {
            averageValue > 0.74f -> Color.parseColor("#1A000000")
            averageValue < 0.34f -> Color.parseColor("#1FFFFFFF")
            else -> adjustAlpha(ColorUtils.blendARGB(overlayColor, surfaceColor, 0.35f), 0.12f)
        }
        val cardColor = adjustAlpha(ColorUtils.blendARGB(surfaceColor, Color.WHITE, 0.18f), 0.86f)
        val cardStrokeColor = adjustAlpha(ColorUtils.blendARGB(overlayColor, surfaceColor, 0.40f), 0.26f)
        return CoverColorScheme(
            imageKey = "",
            mainColor = correctedMain,
            surfaceColor = surfaceColor,
            overlayColor = overlayColor,
            textPrimaryColor = textPrimaryColor,
            textSecondaryColor = textSecondaryColor,
            buttonColor = buttonColor,
            buttonContentColor = buttonContentColor,
            heroFogColor = heroFogColor,
            cardColor = cardColor,
            cardStrokeColor = cardStrokeColor
        )
    }

    private fun correctMainColor(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = hsv[1].coerceIn(0.35f, 0.75f)
        hsv[2] = when {
            hsv[2] < 0.45f -> 0.55f
            hsv[2] > 0.85f -> 0.80f
            else -> hsv[2].coerceIn(0.50f, 0.82f)
        }
        return Color.HSVToColor(hsv)
    }

    private fun createSurfaceColor(mainColor: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(mainColor, hsv)
        hsv[1] = (hsv[1] * 0.46f).coerceIn(0.16f, 0.32f)
        hsv[2] = (0.88f + (hsv[2] - 0.50f) * 0.10f).coerceIn(0.88f, 0.94f)
        return Color.HSVToColor(hsv)
    }

    private fun createOverlayColor(mainColor: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(mainColor, hsv)
        hsv[1] = (hsv[1] * 0.82f).coerceIn(0.28f, 0.68f)
        hsv[2] = (hsv[2] * 0.90f).coerceIn(0.55f, 0.75f)
        return Color.HSVToColor(hsv)
    }

    private fun createButtonColor(mainColor: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(mainColor, hsv)
        hsv[1] = (hsv[1] * 1.06f).coerceIn(0.32f, 0.78f)
        hsv[2] = (hsv[2] * 0.66f).coerceIn(0.34f, 0.58f)
        return Color.HSVToColor(hsv)
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val diff = abs(a - b)
        return min(diff, 360f - diff)
    }

    private fun adjustAlpha(color: Int, alpha: Float): Int {
        return ColorUtils.setAlphaComponent(color, (alpha.coerceIn(0f, 1f) * 255).toInt())
    }

    fun blendSchemes(from: CoverColorScheme, to: CoverColorScheme, fraction: Float): CoverColorScheme {
        val t = fraction.coerceIn(0f, 1f)
        return CoverColorScheme(
            imageKey = to.imageKey,
            mainColor = blendColor(from.mainColor, to.mainColor, t),
            surfaceColor = blendColor(from.surfaceColor, to.surfaceColor, t),
            overlayColor = blendColor(from.overlayColor, to.overlayColor, t),
            textPrimaryColor = blendColor(from.textPrimaryColor, to.textPrimaryColor, t),
            textSecondaryColor = blendColor(from.textSecondaryColor, to.textSecondaryColor, t),
            buttonColor = blendColor(from.buttonColor, to.buttonColor, t),
            buttonContentColor = blendColor(from.buttonContentColor, to.buttonContentColor, t),
            heroFogColor = blendColor(from.heroFogColor, to.heroFogColor, t),
            cardColor = blendColor(from.cardColor, to.cardColor, t),
            cardStrokeColor = blendColor(from.cardStrokeColor, to.cardStrokeColor, t)
        )
    }

    private fun blendColor(from: Int, to: Int, fraction: Float): Int {
        return evaluator.evaluate(fraction, from, to) as Int
    }

    private data class PixelSample(
        val color: Int,
        val h: Float,
        val s: Float,
        val v: Float,
        val bottomWeight: Float
    )

    private data class ColorCluster(
        val averageColor: Int,
        val count: Int,
        val avgSaturation: Float,
        val avgBottomWeight: Float
    )

    private class KMeansCenter(
        var h: Float,
        var s: Float,
        var v: Float
    ) {
        var count: Int = 0
            private set
        private var hueSin = 0.0
        private var hueCos = 0.0
        private var saturationSum = 0.0
        private var valueSum = 0.0
        private var redSum = 0L
        private var greenSum = 0L
        private var blueSum = 0L
        private var bottomWeightSum = 0.0

        fun clear() {
            count = 0
            hueSin = 0.0
            hueCos = 0.0
            saturationSum = 0.0
            valueSum = 0.0
            redSum = 0L
            greenSum = 0L
            blueSum = 0L
            bottomWeightSum = 0.0
        }

        fun add(sample: PixelSample) {
            val radians = Math.toRadians(sample.h.toDouble())
            hueSin += kotlin.math.sin(radians)
            hueCos += kotlin.math.cos(radians)
            saturationSum += sample.s.toDouble()
            valueSum += sample.v.toDouble()
            redSum += Color.red(sample.color).toLong()
            greenSum += Color.green(sample.color).toLong()
            blueSum += Color.blue(sample.color).toLong()
            bottomWeightSum += sample.bottomWeight.toDouble()
            count += 1
        }

        fun recalculate() {
            if (count == 0) return
            h = ((Math.toDegrees(kotlin.math.atan2(hueSin, hueCos)) + 360.0) % 360.0).toFloat()
            s = (saturationSum / count.toDouble()).toFloat()
            v = (valueSum / count.toDouble()).toFloat()
        }

        fun toColorCluster(): ColorCluster {
            val averageColor = Color.rgb(
                (redSum / count.coerceAtLeast(1)).toInt().coerceIn(0, 255),
                (greenSum / count.coerceAtLeast(1)).toInt().coerceIn(0, 255),
                (blueSum / count.coerceAtLeast(1)).toInt().coerceIn(0, 255)
            )
            return ColorCluster(
                averageColor = averageColor,
                count = count,
                avgSaturation = s,
                avgBottomWeight = (bottomWeightSum / count.coerceAtLeast(1).toDouble()).toFloat()
            )
        }
    }
}
