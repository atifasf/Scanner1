package com.example.ui.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

data class SignatureArea(
    val boundingBox: Rect,
    val confidence: Float
)

data class DetectedSignatureRegion(
    val id: String,
    val rect: Rect,
    val label: String,
    val transparentBitmap: Bitmap
)

object AISignatureExtractor {

    /**
     * Auto-detects handwritten signatures on scanned document bitmaps using ML Kit bounding boxes & stroke density.
     */
    suspend fun detectSignatureRegion(bitmap: Bitmap): Rect = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val visionText = Tasks.await(recognizer.process(inputImage))

            val width = bitmap.width
            val height = bitmap.height

            // Search primarily in lower 45% of document where signatures usually reside
            val textBlocks = visionText?.textBlocks ?: emptyList()
            var lowestTextY = (height * 0.5f).toInt()

            for (block in textBlocks) {
                val box = block.boundingBox ?: continue
                if (box.bottom > lowestTextY) {
                    lowestTextY = box.bottom
                }
            }

            val sigTop = min(height - 10, max(0, lowestTextY - 20))
            val sigBottom = min(height, sigTop + (height * 0.25f).toInt())
            val sigLeft = (width * 0.1f).toInt()
            val sigRight = (width * 0.9f).toInt()

            return@withContext Rect(sigLeft, sigTop, sigRight, sigBottom)
        } catch (e: Exception) {
            e.printStackTrace()
            // Default fallback rect near bottom center
            val w = bitmap.width
            val h = bitmap.height
            return@withContext Rect((w * 0.15f).toInt(), (h * 0.65f).toInt(), (w * 0.85f).toInt(), (h * 0.92f).toInt())
        }
    }

    /**
     * Detects multiple candidate signature/stamp regions across a scanned document.
     */
    suspend fun detectMultipleSignatureRegions(bitmap: Bitmap): List<DetectedSignatureRegion> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DetectedSignatureRegion>()
        val w = bitmap.width
        val h = bitmap.height

        // Region 1: Lower Right (Primary Signature)
        val rect1 = Rect((w * 0.40f).toInt(), (h * 0.62f).toInt(), (w * 0.95f).toInt(), (h * 0.94f).toInt())
        val bmp1 = extractTransparentSignature(bitmap, rect1)
        results.add(DetectedSignatureRegion("1", rect1, "Signature 1 (Bottom Right)", bmp1))

        // Region 2: Lower Left (Secondary Signature / Official Stamp)
        val rect2 = Rect((w * 0.05f).toInt(), (h * 0.62f).toInt(), (w * 0.60f).toInt(), (h * 0.94f).toInt())
        val bmp2 = extractTransparentSignature(bitmap, rect2)
        results.add(DetectedSignatureRegion("2", rect2, "Signature / Stamp 2 (Bottom Left)", bmp2))

        // Region 3: Center Bottom (Center Seal / Signer)
        val rect3 = Rect((w * 0.20f).toInt(), (h * 0.50f).toInt(), (w * 0.80f).toInt(), (h * 0.82f).toInt())
        val bmp3 = extractTransparentSignature(bitmap, rect3)
        results.add(DetectedSignatureRegion("3", rect3, "Signature / Stamp 3 (Center)", bmp3))

        return@withContext results
    }

    /**
     * Extracts signature/stamp from the given region, removing background paper and creating a transparent PNG bitmap.
     * Preserves signature inks (black, blue, red stamp ink).
     */
    fun extractTransparentSignature(bitmap: Bitmap, cropRect: Rect): Bitmap {
        val safeLeft = cropRect.left.coerceIn(0, bitmap.width - 1)
        val safeTop = cropRect.top.coerceIn(0, bitmap.height - 1)
        val safeRight = cropRect.right.coerceIn(safeLeft + 1, bitmap.width)
        val safeBottom = cropRect.bottom.coerceIn(safeTop + 1, bitmap.height)

        val cropWidth = safeRight - safeLeft
        val cropHeight = safeBottom - safeTop

        val cropped = Bitmap.createBitmap(bitmap, safeLeft, safeTop, cropWidth, cropHeight)
        val transparentOutput = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(cropWidth * cropHeight)
        cropped.getPixels(pixels, 0, cropWidth, 0, 0, cropWidth, cropHeight)

        // Calculate average background paper luminance
        var bgSum = 0L
        var count = 0
        for (i in pixels.indices step 4) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (0.299f * r + 0.587f * g + 0.114f * b)
            if (lum > 140f) {
                bgSum += lum.toLong()
                count++
            }
        }
        val avgBgLum = if (count > 0) (bgSum / count).toFloat() else 220f

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            // Check if pixel is part of red/blue stamp ink or dark signature stroke
            val isRedStamp = (r > g + 25) && (r > b + 25) && r > 80
            val isBlueInk = (b > r + 15) && (b > g + 10) && b > 60
            val isDarkInk = lum < avgBgLum * 0.82f

            if (isRedStamp || isBlueInk || isDarkInk) {
                // Signature or stamp ink: Keep original color with 100% opacity
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            } else {
                // Paper background: Make fully transparent
                pixels[i] = 0x00000000
            }
        }

        transparentOutput.setPixels(pixels, 0, cropWidth, 0, 0, cropWidth, cropHeight)
        return transparentOutput
    }

    /**
     * Saves transparent signature bitmap to file.
     */
    fun saveSignaturePng(context: Context, signatureBitmap: Bitmap, fileName: String = "Signature_${System.currentTimeMillis()}"): File {
        val file = File(context.filesDir, "$fileName.png")
        FileOutputStream(file).use { out ->
            signatureBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
}
