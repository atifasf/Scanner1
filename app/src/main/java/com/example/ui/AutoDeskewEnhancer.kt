package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class QuadPoints(
    var topLeft: PointF,
    var topRight: PointF,
    var bottomRight: PointF,
    var bottomLeft: PointF
) {
    fun toFloatArray(): FloatArray {
        return floatArrayOf(
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomRight.x, bottomRight.y,
            bottomLeft.x, bottomLeft.y
        )
    }

    fun copyPoints(): QuadPoints {
        return QuadPoints(
            PointF(topLeft.x, topLeft.y),
            PointF(topRight.x, topRight.y),
            PointF(bottomRight.x, bottomRight.y),
            PointF(bottomLeft.x, bottomLeft.y)
        )
    }
}

enum class EnhancementMode {
    MAGIC_COLOR,    // CamScanner style: background whitened, text sharpened, shadows reduced
    BLACK_AND_WHITE,// Crisp B&W
    GRAYSCALE,      // Clean grayscale
    ORIGINAL        // Original color with light contrast boost
}

object AutoDeskewEnhancer {

    /**
     * Complete pipeline: Detect document boundary -> Perspective warp -> Text line deskew -> Whitening & sharpening
     */
    suspend fun autoProcessDocument(
        context: Context,
        original: Bitmap,
        mode: EnhancementMode = EnhancementMode.MAGIC_COLOR,
        skipCrop: Boolean = true, // Preserve exact document position/crop selected by user by default
        enableAutoDeskew: Boolean = false // Optional auto deskew, OFF by default
    ): Bitmap = withContext(Dispatchers.IO) {
        try {
            val deskewed: Bitmap
            var warped = original

            if (!skipCrop) {
                // Step 1: Detect document boundary
                val corners = detectCorners(original)

                // Step 2: Perspective warp to rectangular page
                warped = warpPerspective(original, corners)
            }

            // Step 3: Optional auto deskew (OFF by default)
            if (enableAutoDeskew) {
                val deskewAngle = detectTextSkewAngle(warped)
                deskewed = if (kotlin.math.abs(deskewAngle) > 0.4f) {
                    rotateBitmap(warped, deskewAngle)
                } else {
                    warped
                }
            } else {
                deskewed = warped
            }

            if (deskewed != warped && warped != original) {
                warped.recycle()
            }

            // Step 4: Background whitening, shadow reduction, text sharpening
            val enhanced = enhanceDocument(deskewed, mode)

            val shadowCleaned = com.example.ui.ai.AIShadowRemover.removeShadows(enhanced)
            val finalResult = com.example.ui.ai.AIFingerRemover.removeFingers(shadowCleaned)

            if (deskewed != original && deskewed != enhanced) {
                deskewed.recycle()
            }
            if (enhanced != finalResult && enhanced != shadowCleaned) {
                enhanced.recycle()
            }
            if (shadowCleaned != finalResult) {
                shadowCleaned.recycle()
            }

            return@withContext finalResult
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: Return enhanced original if error occurs
            return@withContext enhanceDocument(original, mode)
        }
    }

    /**
     * Detect document corner points in a bitmap.
     */
    fun detectCorners(bitmap: Bitmap): QuadPoints {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        // Downsample for fast edge detection
        val sampleSize = max(1, max(bitmap.width, bitmap.height) / 400)
        val sampledWidth = max(10, bitmap.width / sampleSize)
        val sampledHeight = max(10, bitmap.height / sampleSize)

        val scaled = Bitmap.createScaledBitmap(bitmap, sampledWidth, sampledHeight, false)
        val pixels = IntArray(sampledWidth * sampledHeight)
        scaled.getPixels(pixels, 0, sampledWidth, 0, 0, sampledWidth, sampledHeight)

        // Calculate gradient / edge intensity
        val edges = FloatArray(sampledWidth * sampledHeight)
        for (y in 1 until sampledHeight - 1) {
            for (x in 1 until sampledWidth - 1) {
                val p0 = pixels[(y - 1) * sampledWidth + x]
                val p1 = pixels[(y + 1) * sampledWidth + x]
                val p2 = pixels[y * sampledWidth + (x - 1)]
                val p3 = pixels[y * sampledWidth + (x + 1)]

                val lum0 = (Color.red(p0) + Color.green(p0) + Color.blue(p0)) / 3f
                val lum1 = (Color.red(p1) + Color.green(p1) + Color.blue(p1)) / 3f
                val lum2 = (Color.red(p2) + Color.green(p2) + Color.blue(p2)) / 3f
                val lum3 = (Color.red(p3) + Color.green(p3) + Color.blue(p3)) / 3f

                val gx = abs(lum3 - lum2)
                val gy = abs(lum1 - lum0)
                edges[y * sampledWidth + x] = gx + gy
            }
        }

        // Search candidate corner points near the four quadrants
        val scaleX = w / sampledWidth
        val scaleY = h / sampledHeight

        var bestTL = PointF(w * 0.03f, h * 0.03f)
        var bestTR = PointF(w * 0.97f, h * 0.03f)
        var bestBR = PointF(w * 0.97f, h * 0.97f)
        var bestBL = PointF(w * 0.03f, h * 0.97f)

        var minTL = Float.MAX_VALUE
        var minTR = Float.MAX_VALUE
        var minBR = Float.MAX_VALUE
        var minBL = Float.MAX_VALUE

        val centerX = sampledWidth / 2f
        val centerY = sampledHeight / 2f

        for (y in 0 until sampledHeight) {
            for (x in 0 until sampledWidth) {
                val edgeVal = edges[y * sampledWidth + x]
                if (edgeVal > 25f) { // Edge threshold
                    val px = x * scaleX
                    val py = y * scaleY

                    // Corner score metrics (distance from outer edge minus edge weight)
                    val scoreTL = px + py - edgeVal * 0.5f
                    val scoreTR = (w - px) + py - edgeVal * 0.5f
                    val scoreBR = (w - px) + (h - py) - edgeVal * 0.5f
                    val scoreBL = px + (h - py) - edgeVal * 0.5f

                    if (x < centerX && y < centerY && scoreTL < minTL) {
                        minTL = scoreTL
                        bestTL = PointF(px, py)
                    }
                    if (x >= centerX && y < centerY && scoreTR < minTR) {
                        minTR = scoreTR
                        bestTR = PointF(px, py)
                    }
                    if (x >= centerX && y >= centerY && scoreBR < minBR) {
                        minBR = scoreBR
                        bestBR = PointF(px, py)
                    }
                    if (x < centerX && y >= centerY && scoreBL < minBL) {
                        minBL = scoreBL
                        bestBL = PointF(px, py)
                    }
                }
            }
        }

        if (scaled != bitmap) scaled.recycle()

        // Sanity bounds check: Ensure points form a valid quadrilateral
        val minWidth = w * 0.2f
        val minHeight = h * 0.2f
        if (abs(bestTR.x - bestTL.x) < minWidth || abs(bestBR.y - bestTR.y) < minHeight) {
            // Default 3% margin quad if detection is inconclusive
            return QuadPoints(
                PointF(w * 0.03f, h * 0.03f),
                PointF(w * 0.97f, h * 0.03f),
                PointF(w * 0.97f, h * 0.97f),
                PointF(w * 0.03f, h * 0.97f)
            )
        }

        return QuadPoints(bestTL, bestTR, bestBR, bestBL)
    }

    /**
     * Perspective warp from 4 source corners to a rectangular destination bitmap.
     */
    fun warpPerspective(bitmap: Bitmap, quad: QuadPoints): Bitmap {
        val topWidth = hypot(quad.topRight.x - quad.topLeft.x, quad.topRight.y - quad.topLeft.y)
        val bottomWidth = hypot(quad.bottomRight.x - quad.bottomLeft.x, quad.bottomRight.y - quad.bottomLeft.y)
        val targetWidth = max(200f, max(topWidth, bottomWidth)).roundToInt()

        val leftHeight = hypot(quad.bottomLeft.x - quad.topLeft.x, quad.bottomLeft.y - quad.topLeft.y)
        val rightHeight = hypot(quad.bottomRight.x - quad.topRight.x, quad.bottomRight.y - quad.topRight.y)
        val targetHeight = max(200f, max(leftHeight, rightHeight)).roundToInt()

        val srcPoints = quad.toFloatArray()
        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        if (!success) {
            return bitmap
        }

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }

        canvas.drawBitmap(bitmap, matrix, paint)
        return output
    }

    /**
     * Detect text line skew angle using ML Kit Text Recognition.
     */
    suspend fun detectTextSkewAngle(bitmap: Bitmap): Float = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val visionText = Tasks.await(recognizer.process(inputImage)) ?: return@withContext 0f

            val angles = mutableListOf<Float>()
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val corners = line.cornerPoints
                    if (corners != null && corners.size >= 2) {
                        val dx = (corners[1].x - corners[0].x).toFloat()
                        val dy = (corners[1].y - corners[0].y).toFloat()
                        if (hypot(dx, dy) > 30f) {
                            val angleRad = atan2(dy, dx)
                            var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()
                            while (angleDeg > 45f) angleDeg -= 90f
                            while (angleDeg < -45f) angleDeg += 90f
                            angles.add(angleDeg)
                        }
                    }
                }
            }

            if (angles.isEmpty()) return@withContext 0f

            angles.sort()
            val medianAngle = angles[angles.size / 2]
            return@withContext medianAngle
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext 0f
        }
    }

    /**
     * Rotate a bitmap by a specified angle in degrees.
     */
    fun rotateBitmap(bitmap: Bitmap, angle: Float): Bitmap {
        if (abs(angle) < 0.1f) return bitmap
        val matrix = Matrix().apply {
            postRotate(-angle, bitmap.width / 2f, bitmap.height / 2f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Enhance document with whitening, shadow removal, and text sharpening (CamScanner style).
     */
    fun enhanceDocument(bitmap: Bitmap, mode: EnhancementMode): Bitmap {
        if (mode == EnhancementMode.ORIGINAL) {
            return ImageEnhancer.enhanceBitmap(bitmap)
        }

        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Estimate local background illumination using a downscaled blur map
        val gridStep = max(1, max(width, height) / 32)
        val gridW = max(1, width / gridStep)
        val gridH = max(1, height / gridStep)
        val bgGrid = FloatArray(gridW * gridH)

        for (gy in 0 until gridH) {
            for (gx in 0 until gridW) {
                var maxLum = 0f
                val startX = gx * gridStep
                val endX = min(width, (gx + 1) * gridStep)
                val startY = gy * gridStep
                val endY = min(height, (gy + 1) * gridStep)

                for (py in startY until endY step 2) {
                    for (px in startX until endX step 2) {
                        val c = pixels[py * width + px]
                        val r = (c shr 16) and 0xFF
                        val g = (c shr 8) and 0xFF
                        val b = c and 0xFF
                        val lum = 0.299f * r + 0.587f * g + 0.114f * b
                        if (lum > maxLum) maxLum = lum
                    }
                }
                bgGrid[gy * gridW + gx] = max(100f, maxLum)
            }
        }

        // Apply adaptive whitening & text contrast enhancement
        for (y in 0 until height) {
            val gy = min(gridH - 1, y / gridStep)
            for (x in 0 until width) {
                val gx = min(gridW - 1, x / gridStep)
                val index = y * width + x
                val c = pixels[index]

                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val alpha = (c shr 24) and 0xFF

                val bgLum = bgGrid[gy * gridW + gx]
                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                when (mode) {
                    EnhancementMode.MAGIC_COLOR -> {
                        // Normalize background to pure white while preserving text/ink color
                        val scale = 255f / max(1f, bgLum)
                        var newR = (r * scale).roundToInt()
                        var newG = (g * scale).roundToInt()
                        var newB = (b * scale).roundToInt()

                        // Contrast stretch text ink
                        if (lum < bgLum * 0.75f) {
                            val factor = lum / (bgLum * 0.75f)
                            newR = (newR * factor * 0.85f).roundToInt()
                            newG = (newG * factor * 0.85f).roundToInt()
                            newB = (newB * factor * 0.85f).roundToInt()
                        } else {
                            // Smoothly transition background pixels to high white
                            val bgFactor = (lum - bgLum * 0.75f) / (bgLum * 0.25f)
                            val boost = bgFactor * 25f
                            newR = min(255, (newR + boost).roundToInt())
                            newG = min(255, (newG + boost).roundToInt())
                            newB = min(255, (newB + boost).roundToInt())
                        }

                        pixels[index] = (alpha shl 24) or
                                (min(255, max(0, newR)) shl 16) or
                                (min(255, max(0, newG)) shl 8) or
                                min(255, max(0, newB))
                    }

                    EnhancementMode.BLACK_AND_WHITE -> {
                        val normalizedLum = (lum / bgLum * 255f).coerceIn(0f, 255f)
                        val bwVal = if (normalizedLum < 180f) {
                            (normalizedLum * 0.5f).roundToInt().coerceIn(0, 255)
                        } else {
                            255
                        }
                        pixels[index] = (alpha shl 24) or (bwVal shl 16) or (bwVal shl 8) or bwVal
                    }

                    EnhancementMode.GRAYSCALE -> {
                        val scale = 255f / max(1f, bgLum)
                        var gray = (lum * scale).roundToInt().coerceIn(0, 255)
                        if (gray > 220) gray = 255
                        pixels[index] = (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray
                    }

                    EnhancementMode.ORIGINAL -> {}
                }
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}
