package com.liujiaming.embypro

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Data class holding curated button colors derived from artwork.
 */
data class MaterialYouButtonColors(
    val seed: Int,
    val container: Int,
    val content: Int
)

/**
 * Data class holding multi-stop backdrop colors for the video detail page.
 */
data class MaterialYouBackdropColors(
    val pageTop: Int,
    val pageUpper: Int,
    val pageMid: Int,
    val pageBottom: Int,
    val heroBlendTop: Int,
    val heroBlendUpper: Int,
    val heroBlendMid: Int,
    val heroBlendBottom: Int
)

/**
 * Extracts aesthetically-curated colors from cover art instead of using a flat average.
 * Sampling is biased toward the lower artwork area so UI colors follow the visually denser region.
 */
object MaterialYouColorHelper {

    fun extractButtonColors(bitmap: Bitmap, fallbackSeed: Int): MaterialYouButtonColors {
        val seed = extractBaseColor(bitmap, fallbackSeed)
        val container = createButtonColor(seed)
        val content = if (ColorUtils.calculateLuminance(container) > 0.42) Color.BLACK else Color.WHITE
        return MaterialYouButtonColors(seed = seed, container = container, content = content)
    }

    fun extractBackdropColors(seed: Int): MaterialYouBackdropColors {
        return MaterialYouBackdropColors(
            pageTop = createPageColor(seed, whiteBlend = 0.72f, saturationScale = 0.82f, lightnessBoost = 0.02f),
            pageUpper = createPageColor(seed, whiteBlend = 0.78f, saturationScale = 0.74f, lightnessBoost = 0.03f),
            pageMid = createPageColor(seed, whiteBlend = 0.84f, saturationScale = 0.66f, lightnessBoost = 0.04f),
            pageBottom = createPageColor(seed, whiteBlend = 0.90f, saturationScale = 0.58f, lightnessBoost = 0.05f),
            heroBlendTop = Color.TRANSPARENT,
            heroBlendUpper = adjustAlpha(createPageColor(seed, whiteBlend = 0.64f, saturationScale = 0.88f), 0.12f),
            heroBlendMid = adjustAlpha(createPageColor(seed, whiteBlend = 0.72f, saturationScale = 0.76f), 0.68f),
            heroBlendBottom = createPageColor(seed, whiteBlend = 0.82f, saturationScale = 0.68f, lightnessBoost = 0.03f)
        )
    }

    private fun extractBaseColor(bitmap: Bitmap, fallbackSeed: Int): Int {
        if (bitmap.width <= 0 || bitmap.height <= 0) return fallbackSeed

        val cropTop = (bitmap.height * 0.55f).roundToInt().coerceIn(0, bitmap.height - 1)
        val cropHeight = (bitmap.height - cropTop).coerceAtLeast(1)
        val sampledRegion = runCatching {
            Bitmap.createBitmap(bitmap, 0, cropTop, bitmap.width, cropHeight)
        }.getOrElse { bitmap }
        val sampleSize = 72
        val sampledBitmap = Bitmap.createScaledBitmap(
            sampledRegion,
            sampleSize.coerceAtMost(sampledRegion.width.coerceAtLeast(1)),
            sampleSize.coerceAtMost(sampledRegion.height.coerceAtLeast(1)),
            true
        )

        val clusters = HashMap<Int, WeightedColorCluster>()
        val pixelHsl = FloatArray(3)
        val maxX = sampledBitmap.width
        val maxY = sampledBitmap.height

        for (y in 0 until maxY) {
            for (x in 0 until maxX) {
                val color = sampledBitmap.getPixel(x, y)
                if (Color.alpha(color) < 180) continue
                ColorUtils.colorToHSL(color, pixelHsl)
                val weight = scoreCandidate(color, pixelHsl)
                if (weight <= 0f) continue

                val hueBin = ((pixelHsl[0] / 15f).toInt()).coerceIn(0, 23)
                val satBin = ((pixelHsl[1] / 0.12f).toInt()).coerceIn(0, 8)
                val lightBin = ((pixelHsl[2] / 0.12f).toInt()).coerceIn(0, 8)
                val key = (hueBin shl 16) or (satBin shl 8) or lightBin
                val cluster = clusters.getOrPut(key) { WeightedColorCluster() }
                cluster.add(pixelHsl, weight)
            }
        }

        val bestCluster = clusters.values.maxByOrNull { cluster ->
            val averageHsl = cluster.averageHsl()
            val saturationPreference = 1f - abs(averageHsl[1] - 0.42f)
            val lightnessPreference = 1f - abs(averageHsl[2] - 0.48f)
            cluster.totalWeight * (0.72f + 0.16f * saturationPreference + 0.12f * lightnessPreference)
        } ?: return fallbackSeed

        return ColorUtils.HSLToColor(bestCluster.averageHsl())
    }

    private fun scoreCandidate(color: Int, hsl: FloatArray): Float {
        val luminance = ColorUtils.calculateLuminance(color).toFloat()
        val saturation = hsl[1]
        val lightness = hsl[2]
        val hue = hsl[0]

        if (luminance > 0.85f || luminance < 0.15f) return 0f
        if (saturation < 0.08f) return 0f
        if (isNearNeutral(color, saturation, luminance)) return 0f

        var weight = 1f
        weight *= normalize(saturation, 0.10f, 0.58f) * 0.55f + 0.45f
        weight *= (1f - abs(lightness - 0.50f) / 0.50f).coerceIn(0.35f, 1f)

        if (hue in 22f..72f) {
            weight *= 0.42f
        } else if (hue in 8f..30f && saturation in 0.12f..0.52f && lightness in 0.34f..0.78f) {
            weight *= 0.58f
        }

        return weight
    }

    private fun createPageColor(
        seed: Int,
        whiteBlend: Float,
        saturationScale: Float,
        lightnessBoost: Float = 0f
    ): Int {
        val tunedSeed = createPageSeed(seed)
        val blended = ColorUtils.blendARGB(tunedSeed, Color.WHITE, whiteBlend.coerceIn(0.70f, 0.85f))
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(blended, hsl)
        hsl[1] = (hsl[1] * saturationScale.coerceIn(0.60f, 0.80f)).coerceIn(0.08f, 0.28f)
        hsl[2] = (hsl[2] + lightnessBoost).coerceIn(0.84f, 0.96f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun createPageSeed(seed: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed, hsl)

        if (hsl[0] in 20f..80f) {
            val hueShift = when {
                hsl[0] < 35f -> 16f
                hsl[0] < 55f -> 24f
                else -> 32f
            }
            hsl[0] = (hsl[0] + hueShift) % 360f
        }

        hsl[1] = (hsl[1] * 0.82f).coerceIn(0.16f, 0.46f)
        hsl[2] = hsl[2].coerceIn(0.32f, 0.62f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun createButtonColor(seed: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed, hsl)
        hsl[1] = (hsl[1] * 1.10f).coerceIn(0.28f, 0.84f)
        hsl[2] = (hsl[2] * 0.80f).coerceIn(0.24f, 0.48f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun isNearNeutral(color: Int, saturation: Float, luminance: Float): Boolean {
        if (saturation < 0.12f) return true
        val max = maxOf(Color.red(color), Color.green(color), Color.blue(color))
        val min = minOf(Color.red(color), Color.green(color), Color.blue(color))
        if (max - min < 18) return true
        if (luminance > 0.92f || luminance < 0.08f) return true
        return false
    }

    private fun adjustAlpha(color: Int, alpha: Float): Int {
        return ColorUtils.setAlphaComponent(color, (alpha.coerceIn(0f, 1f) * 255).roundToInt())
    }

    private fun normalize(value: Float, min: Float, max: Float): Float {
        return ((value - min) / (max - min)).coerceIn(0f, 1f)
    }

    private class WeightedColorCluster {
        var totalWeight = 0f
            private set
        private var hueSin = 0.0
        private var hueCos = 0.0
        private var saturationSum = 0.0
        private var lightnessSum = 0.0

        fun add(hsl: FloatArray, weight: Float) {
            val radians = Math.toRadians(hsl[0].toDouble())
            hueSin += sin(radians) * weight
            hueCos += cos(radians) * weight
            saturationSum += hsl[1] * weight
            lightnessSum += hsl[2] * weight
            totalWeight += weight
        }

        fun averageHsl(): FloatArray {
            if (totalWeight <= 0f) return floatArrayOf(220f, 0.18f, 0.48f)
            val hue = ((Math.toDegrees(atan2(hueSin, hueCos)) + 360.0) % 360.0).toFloat()
            return floatArrayOf(
                hue,
                (saturationSum / totalWeight).toFloat(),
                (lightnessSum / totalWeight).toFloat()
            )
        }
    }
}
