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
    val heroBlendBottom: Int,
    val glowTop: Int,
    val glowMid: Int,
    val glowBottom: Int
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
        val heroTint = createHeroTint(seed)
        return MaterialYouBackdropColors(
            pageTop = createPageColor(seed, whiteBlend = 0.34f, saturationScale = 0.98f, lightnessBoost = 0.00f),
            pageUpper = createPageColor(seed, whiteBlend = 0.46f, saturationScale = 0.90f, lightnessBoost = 0.01f),
            pageMid = createPageColor(seed, whiteBlend = 0.58f, saturationScale = 0.82f, lightnessBoost = 0.03f),
            pageBottom = createPageColor(seed, whiteBlend = 0.70f, saturationScale = 0.74f, lightnessBoost = 0.04f),
            heroBlendTop = Color.TRANSPARENT,
            heroBlendUpper = adjustAlpha(heroTint, 0.16f),
            heroBlendMid = adjustAlpha(ColorUtils.blendARGB(heroTint, Color.WHITE, 0.08f), 0.40f),
            heroBlendBottom = ColorUtils.blendARGB(
                createPageColor(seed, whiteBlend = 0.56f, saturationScale = 0.84f, lightnessBoost = 0.02f),
                heroTint,
                0.26f
            ),
            glowTop = Color.TRANSPARENT,
            glowMid = adjustAlpha(ColorUtils.blendARGB(heroTint, Color.WHITE, 0.06f), 0.24f),
            glowBottom = adjustAlpha(ColorUtils.blendARGB(heroTint, createPageColor(seed, 0.48f, 0.88f), 0.22f), 0.64f)
        )
    }

    private fun extractBaseColor(bitmap: Bitmap, fallbackSeed: Int): Int {
        if (bitmap.width <= 0 || bitmap.height <= 0) return fallbackSeed

        val cropTop = (bitmap.height * 0.34f).roundToInt().coerceIn(0, bitmap.height - 1)
        val cropBottom = (bitmap.height * 0.88f).roundToInt().coerceIn(cropTop + 1, bitmap.height)
        val cropHeight = (cropBottom - cropTop).coerceAtLeast(1)
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
                val spatialWeight = calculateSpatialWeight(
                    x = x.toFloat() / maxX.coerceAtLeast(1).toFloat(),
                    y = y.toFloat() / maxY.coerceAtLeast(1).toFloat()
                )
                val weight = scoreCandidate(color, pixelHsl) * spatialWeight
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
        weight *= normalize(saturation, 0.12f, 0.64f) * 0.62f + 0.38f
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
        val blended = ColorUtils.blendARGB(tunedSeed, Color.WHITE, whiteBlend.coerceIn(0.32f, 0.74f))
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(blended, hsl)
        hsl[1] = (hsl[1] * saturationScale.coerceIn(0.72f, 1.02f)).coerceIn(0.20f, 0.46f)
        hsl[2] = (hsl[2] + lightnessBoost).coerceIn(0.70f, 0.86f)
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

        if (hsl[0] in 160f..220f) {
            hsl[0] += (208f - hsl[0]) * 0.28f
        }
        hsl[1] = (hsl[1] * 0.98f).coerceIn(0.34f, 0.72f)
        hsl[2] = hsl[2].coerceIn(0.30f, 0.52f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun createHeroTint(seed: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed, hsl)
        if (hsl[0] in 145f..225f) {
            hsl[0] += (206f - hsl[0]) * 0.24f
        }
        hsl[1] = (hsl[1] * 1.10f).coerceIn(0.42f, 0.82f)
        hsl[2] = hsl[2].coerceIn(0.34f, 0.56f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun createButtonColor(seed: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed, hsl)
        if (hsl[0] in 150f..220f) {
            hsl[0] += (214f - hsl[0]) * 0.35f
        }
        hsl[1] = (hsl[1] * 1.04f).coerceIn(0.34f, 0.72f)
        hsl[2] = (hsl[2] * 0.72f).coerceIn(0.34f, 0.48f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun calculateSpatialWeight(x: Float, y: Float): Float {
        val horizontalFocus = 1f - abs(x - 0.42f).coerceAtMost(1f)
        val verticalFocus = 1f - abs(y - 0.54f).coerceAtMost(1f)
        return (0.58f + horizontalFocus * 0.24f + verticalFocus * 0.18f).coerceIn(0.55f, 1.15f)
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
