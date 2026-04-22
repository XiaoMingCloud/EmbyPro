package com.liujiaming.embypro

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/**
 * Data class holding Material You inspired button colors.
 * Contains seed color, container background, and content (text/icon) color.
 */
data class MaterialYouButtonColors(
    val seed: Int,
    val container: Int,
    val content: Int
)

/**
 * Data class holding Material You inspired backdrop (background) colors.
 * Provides gradient colors for page background and hero section blending.
 */
data class MaterialYouBackdropColors(
    val pageTop: Int,
    val pageMid: Int,
    val pageBottom: Int,
    val heroBlendTop: Int,
    val heroBlendBottom: Int
)

/**
 * Utility for extracting Material You inspired color palettes from images.
 * Uses Android Palette library to analyze bitmaps and generate harmonious color schemes.
 */
object MaterialYouColorHelper {

    /**
     * Extracts button colors from a bitmap image.
     * Analyzes the image to find an optimal seed color, then creates container and content colors.
     *
     * @param bitmap The source image to analyze
     * @param fallbackSeed Fallback color if palette extraction fails
     * @return MaterialYouButtonColors with seed, container, and content colors
     */
    fun extractButtonColors(bitmap: Bitmap, fallbackSeed: Int): MaterialYouButtonColors {
        val palette = Palette.from(bitmap)
            .clearFilters()
            .maximumColorCount(24)
            .generate()

        val seed = pickSeedColor(palette, fallbackSeed)
        val container = createContainerColor(seed)
        val content = if (ColorUtils.calculateLuminance(container) > 0.42) Color.BLACK else Color.WHITE
        return MaterialYouButtonColors(seed = seed, container = container, content = content)
    }

    /**
     * Generates backdrop colors from a seed color.
     * Creates a harmonious gradient by adjusting HSL lightness values.
     *
     * @param seed The base color to derive the palette from
     * @return MaterialYouBackdropColors with page gradient and hero blend colors
     */
    fun extractBackdropColors(seed: Int): MaterialYouBackdropColors {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed, hsl)

        val toned = hsl.copyOf().apply {
            this[1] = this[1].coerceIn(0.16f, 0.42f)
        }

        val top = toned.copyOf().apply { this[2] = 0.74f }
        val mid = toned.copyOf().apply { this[2] = 0.86f }
        val bottom = toned.copyOf().apply { this[2] = 0.93f }
        val blendBottom = toned.copyOf().apply { this[2] = 0.88f }

        return MaterialYouBackdropColors(
            pageTop = ColorUtils.HSLToColor(top),
            pageMid = ColorUtils.HSLToColor(mid),
            pageBottom = ColorUtils.HSLToColor(bottom),
            heroBlendTop = ColorUtils.setAlphaComponent(seed, 0),
            heroBlendBottom = ColorUtils.HSLToColor(blendBottom)
        )
    }

    /**
     * Selects the best seed color from a palette.
     * Scores colors based on saturation, lightness, and population (frequency in image).
     */
    private fun pickSeedColor(palette: Palette, fallbackSeed: Int): Int {
        val swatches = palette.swatches
        if (swatches.isEmpty()) return fallbackSeed

        val best = swatches.maxByOrNull { swatch ->
            val hsl = swatch.hsl
            val saturationScore = normalize(hsl[1], 0.18f, 0.92f)
            val lightnessScore = 1f - kotlin.math.abs(hsl[2] - 0.52f)
            val populationScore = swatch.population.toFloat() / (swatches.maxOfOrNull { it.population } ?: 1)
            saturationScore * 0.45f + lightnessScore * 0.30f + populationScore * 0.25f
        }

        return best?.rgb ?: fallbackSeed
    }

    /**
     * Creates a container color from a seed color.
     * Adjusts saturation and lightness to create a softer, Material You inspired tone.
     */
    private fun createContainerColor(seed: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed, hsl)

        hsl[1] = hsl[1].coerceIn(0.28f, 0.72f)
        hsl[2] = if (hsl[2] < 0.48f) 0.56f else 0.46f

        val tonedSeed = ColorUtils.HSLToColor(hsl)
        return ColorUtils.blendARGB(tonedSeed, Color.WHITE, 0.10f)
    }

    /**
     * Normalizes a value to a 0-1 range within given min/max bounds.
     */
    private fun normalize(value: Float, min: Float, max: Float): Float {
        return ((value - min) / (max - min)).coerceIn(0f, 1f)
    }
}
