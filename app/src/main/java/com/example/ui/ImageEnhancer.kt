package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ImageEnhancer {
    fun enhanceBitmap(original: Bitmap): Bitmap {
        val enhanced = Bitmap.createBitmap(original.width, original.height, original.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        val paint = Paint()

        // Create a ColorMatrix for contrast and brightness
        val colorMatrix = ColorMatrix()
        
        // Increase contrast by 1.2x (20% more contrast)
        val contrast = 1.2f
        // Increase brightness slightly
        val brightness = 10f
        
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        
        // Increase saturation by 1.1x
        val saturationMatrix = ColorMatrix()
        saturationMatrix.setSaturation(1.1f)
        
        colorMatrix.postConcat(contrastMatrix)
        colorMatrix.postConcat(saturationMatrix)
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        
        canvas.drawBitmap(original, 0f, 0f, paint)
        return enhanced
    }

    /**
     * Applies a high-pass unsharp mask and text edge sharpening to deblur fuzzy text characters.
     */
    fun deblurAndSharpenText(original: Bitmap, amount: Float = 0.7f): Bitmap {
        val width = original.width
        val height = original.height
        if (width <= 2 || height <= 2) return original

        val srcPixels = IntArray(width * height)
        val dstPixels = IntArray(width * height)
        original.getPixels(srcPixels, 0, width, 0, 0, width, height)

        for (y in 1 until height - 1) {
            val yOffset = y * width
            val topOffset = (y - 1) * width
            val bottomOffset = (y + 1) * width

            for (x in 1 until width - 1) {
                val idx = yOffset + x

                val c = srcPixels[idx]
                val cTop = srcPixels[topOffset + x]
                val cBottom = srcPixels[bottomOffset + x]
                val cLeft = srcPixels[yOffset + x - 1]
                val cRight = srcPixels[yOffset + x + 1]

                val a = (c shr 24) and 0xFF

                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val rLap = 5 * r - ((cTop shr 16) and 0xFF) - ((cBottom shr 16) and 0xFF) - ((cLeft shr 16) and 0xFF) - ((cRight shr 16) and 0xFF)
                val gLap = 5 * g - ((cTop shr 8) and 0xFF) - ((cBottom shr 8) and 0xFF) - ((cLeft shr 8) and 0xFF) - ((cRight shr 8) and 0xFF)
                val bLap = 5 * b - (cTop and 0xFF) - (cBottom and 0xFF) - (cLeft and 0xFF) - (cRight and 0xFF)

                var sharpR = (r + (rLap - r) * amount).toInt().coerceIn(0, 255)
                var sharpG = (g + (gLap - g) * amount).toInt().coerceIn(0, 255)
                var sharpB = (b + (bLap - b) * amount).toInt().coerceIn(0, 255)

                // Additional ink stroke darkening for blurry dark text
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                if (lum < 160f) {
                    val inkFactor = 0.9f
                    sharpR = (sharpR * inkFactor).toInt().coerceIn(0, 255)
                    sharpG = (sharpG * inkFactor).toInt().coerceIn(0, 255)
                    sharpB = (sharpB * inkFactor).toInt().coerceIn(0, 255)
                }

                dstPixels[idx] = (a shl 24) or (sharpR shl 16) or (sharpG shl 8) or sharpB
            }
        }

        // Copy top/bottom borders
        for (x in 0 until width) {
            dstPixels[x] = srcPixels[x]
            dstPixels[(height - 1) * width + x] = srcPixels[(height - 1) * width + x]
        }
        // Copy left/right borders
        for (y in 0 until height) {
            dstPixels[y * width] = srcPixels[y * width]
            dstPixels[y * width + width - 1] = srcPixels[y * width + width - 1]
        }

        val output = Bitmap.createBitmap(width, height, original.config ?: Bitmap.Config.ARGB_8888)
        output.setPixels(dstPixels, 0, width, 0, 0, width, height)
        return output
    }

    fun applyTextDarknessAndBackgroundClarity(
        original: Bitmap,
        textDarkness: Float,
        backgroundClarity: Float
    ): Bitmap {
        if (textDarkness <= 0f && backgroundClarity <= 0f) return original

        val enhanced = Bitmap.createBitmap(original.width, original.height, original.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        val paint = Paint().apply { isAntiAlias = true }

        val c = 1f + textDarkness * 1.5f
        val bVal = 128f * (1f - c) + backgroundClarity * 150f
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, bVal,
                0f, c, 0f, 0f, bVal,
                0f, 0f, c, 0f, bVal,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(original, 0f, 0f, paint)
        return enhanced
    }
}
