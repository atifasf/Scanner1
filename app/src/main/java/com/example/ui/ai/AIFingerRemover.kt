package com.example.ui.ai

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

object AIFingerRemover {

    /**
     * Detects fingers/thumbs holding edges of documents and fills them with surrounding page background color.
     */
    fun removeFingers(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Calculate reference document background color from non-edge inner margins
        val sampleCols = mutableListOf<Int>()
        val sampleX1 = (width * 0.2f).toInt()
        val sampleX2 = (width * 0.8f).toInt()
        val sampleY1 = (height * 0.2f).toInt()
        val sampleY2 = (height * 0.8f).toInt()

        for (y in sampleY1..sampleY2 step (height / 20).coerceAtLeast(1)) {
            for (x in sampleX1..sampleX2 step (width / 20).coerceAtLeast(1)) {
                sampleCols.add(pixels[y * width + x])
            }
        }

        var avgR = 0
        var avgG = 0
        var avgB = 0
        if (sampleCols.isNotEmpty()) {
            var sumR = 0L; var sumG = 0L; var sumB = 0L
            for (c in sampleCols) {
                sumR += (c shr 16) and 0xFF
                sumG += (c shr 8) and 0xFF
                sumB += c and 0xFF
            }
            avgR = (sumR / sampleCols.size).toInt()
            avgG = (sumG / sampleCols.size).toInt()
            avgB = (sumB / sampleCols.size).toInt()
        } else {
            avgR = 245; avgG = 245; avgB = 245
        }

        val marginX = (width * 0.18f).toInt()
        val marginY = (height * 0.18f).toInt()

        val hsv = FloatArray(3)

        for (y in 0 until height) {
            val isBorderY = y < marginY || y > height - marginY
            for (x in 0 until width) {
                val isBorderX = x < marginX || x > width - marginX
                val idx = y * width + x

                if (isBorderY || isBorderX) {
                    val c = pixels[idx]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF

                    Color.RGBToHSV(r, g, b, hsv)
                    val h = hsv[0]
                    val s = hsv[1]
                    val v = hsv[2]

                    // Human skin tone detection rule in HSV & RGB
                    val isSkinColor = (h in 0f..40f || h in 330f..360f) &&
                            (s in 0.12f..0.75f) &&
                            (v in 0.25f..0.98f) &&
                            (r > g && g > b) &&
                            ((r - g) > 12)

                    if (isSkinColor) {
                        // Replace finger pixel with page background color
                        pixels[idx] = (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
                    }
                }
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}
