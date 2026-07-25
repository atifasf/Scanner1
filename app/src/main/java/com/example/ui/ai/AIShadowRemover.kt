package com.example.ui.ai

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object AIShadowRemover {

    /**
     * Removes uneven shadows (caused by hands, camera, overhead lighting) from document images.
     * Uses adaptive local illumination mapping and ratio gain compensation.
     */
    fun removeShadows(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Step 1: Calculate tile-based illumination map
        val tileSize = max(16, max(width, height) / 32)
        val gridW = max(1, width / tileSize)
        val gridH = max(1, height / tileSize)
        val illumMap = FloatArray(gridW * gridH)

        for (gy in 0 until gridH) {
            for (gx in 0 until gridW) {
                val startX = gx * tileSize
                val endX = min(width, (gx + 1) * tileSize)
                val startY = gy * tileSize
                val endY = min(height, (gy + 1) * tileSize)

                var maxLum = 0f
                // Pick top 10% highest luminance values as local background reference
                val sampleLums = mutableListOf<Float>()
                for (py in startY until endY step 2) {
                    for (px in startX until endX step 2) {
                        val c = pixels[py * width + px]
                        val r = (c shr 16) and 0xFF
                        val g = (c shr 8) and 0xFF
                        val b = c and 0xFF
                        val lum = 0.299f * r + 0.587f * g + 0.114f * b
                        sampleLums.add(lum)
                    }
                }
                sampleLums.sort()
                val topIndex = (sampleLums.size * 0.9f).toInt().coerceIn(0, sampleLums.size - 1)
                maxLum = if (sampleLums.isNotEmpty()) sampleLums[topIndex] else 200f
                illumMap[gy * gridW + gx] = max(80f, maxLum)
            }
        }

        // Target background luminance (95% white)
        val targetBackground = 245f

        // Step 2: Apply adaptive gain compensation per pixel
        for (y in 0 until height) {
            val gy = (y / tileSize).coerceIn(0, gridH - 1)
            for (x in 0 until width) {
                val gx = (x / tileSize).coerceIn(0, gridW - 1)
                val idx = y * width + x
                val c = pixels[idx]

                val a = (c shr 24) and 0xFF
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val localIllum = illumMap[gy * gridW + gx]
                // Gain factor compensates for local shadow darkening
                val gain = (targetBackground / max(1f, localIllum)).coerceIn(1.0f, 2.5f)

                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                val newR: Int
                val newG: Int
                val newB: Int

                if (lum > localIllum * 0.85f) {
                    // Smoothly push paper background pixels to clean bright white
                    val bgRatio = (lum - localIllum * 0.85f) / max(1f, localIllum * 0.15f)
                    val smoothVal = (r * gain + bgRatio * 35f).roundToInt().coerceIn(0, 255)
                    val smoothG = (g * gain + bgRatio * 35f).roundToInt().coerceIn(0, 255)
                    val smoothB = (b * gain + bgRatio * 35f).roundToInt().coerceIn(0, 255)
                    newR = smoothVal
                    newG = smoothG
                    newB = smoothB
                } else {
                    // Preserve text ink color contrast
                    newR = (r * gain * 0.95f).roundToInt().coerceIn(0, 255)
                    newG = (g * gain * 0.95f).roundToInt().coerceIn(0, 255)
                    newB = (b * gain * 0.95f).roundToInt().coerceIn(0, 255)
                }

                pixels[idx] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}
