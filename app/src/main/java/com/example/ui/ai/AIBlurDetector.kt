package com.example.ui.ai

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max

data class BlurCheckResult(
    val isBlurry: Boolean,
    val score: Float,
    val warningMessage: String
)

object AIBlurDetector {

    /**
     * Analyzes image sharpness using directional gradient variance.
     * Score < threshold indicates out-of-focus or motion blur.
     */
    fun checkBlur(bitmap: Bitmap, threshold: Float = 110.0f): BlurCheckResult {
        // Downsample for high performance
        val targetSize = 600
        val sampleSize = max(1, max(bitmap.width, bitmap.height) / targetSize)
        val w = max(10, bitmap.width / sampleSize)
        val h = max(10, bitmap.height / sampleSize)

        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, false)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        var totalGradient = 0.0
        var count = 0

        val laplacianValues = FloatArray(w * h)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val center = getLuminance(pixels[idx])

                val top = getLuminance(pixels[(y - 1) * w + x])
                val bottom = getLuminance(pixels[(y + 1) * w + x])
                val left = getLuminance(pixels[y * w + (x - 1)])
                val right = getLuminance(pixels[y * w + (x + 1)])

                // Discrete 2D Laplacian operator
                val lap = abs(4 * center - top - bottom - left - right)
                laplacianValues[idx] = lap
                totalGradient += lap
                count++
            }
        }

        val meanGradient = if (count > 0) totalGradient / count else 0.0

        // Calculate variance of Laplacian
        var varianceSum = 0.0
        for (i in 0 until (w * h)) {
            val v = laplacianValues[i]
            if (v > 0) {
                val diff = v - meanGradient
                varianceSum += diff * diff
            }
        }

        val variance = if (count > 0) varianceSum / count else 0.0
        if (scaled != bitmap) scaled.recycle()

        val blurScore = variance.toFloat()
        val isBlurry = blurScore < threshold

        return BlurCheckResult(
            isBlurry = isBlurry,
            score = blurScore,
            warningMessage = if (isBlurry) {
                "This scan appears blurry. Please retake for better OCR and document quality."
            } else {
                "Scan quality is crisp and clear."
            }
        )
    }

    private fun getLuminance(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
}
