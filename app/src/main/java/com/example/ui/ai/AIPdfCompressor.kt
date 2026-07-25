package com.example.ui.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

enum class PdfCompressionPreset(val displayName: String, val maxDimension: Int, val jpegQuality: Int) {
    HIGH_QUALITY("High Quality", 1600, 85),
    BALANCED("Balanced", 1200, 65),
    MAXIMUM_COMPRESSION("Maximum Compression", 900, 45)
}

object AIPdfCompressor {

    /**
     * Compress images and generate an optimized PDF file based on the selected preset.
     */
    fun generateOptimizedPdf(
        context: Context,
        imagePaths: List<String>,
        outputFile: File,
        preset: PdfCompressionPreset = PdfCompressionPreset.BALANCED
    ): Boolean {
        val pdfDocument = PdfDocument()

        try {
            imagePaths.forEachIndexed { index, path ->
                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(path, opts)

                val originalW = opts.outWidth
                val originalH = opts.outHeight

                // Calculate sample size for downscaling
                var sampleSize = 1
                val maxDim = preset.maxDimension
                if (originalW > maxDim || originalH > maxDim) {
                    val halfW = originalW / 2
                    val halfH = originalH / 2
                    while ((halfW / sampleSize) >= maxDim && (halfH / sampleSize) >= maxDim) {
                        sampleSize *= 2
                    }
                }

                opts.inJustDecodeBounds = false
                opts.inSampleSize = sampleSize
                val decoded = BitmapFactory.decodeFile(path, opts) ?: return@forEachIndexed

                // Scale bitmap strictly within preset bounds
                val scale = minOf(1.0f, maxDim.toFloat() / maxOf(decoded.width, decoded.height))
                val targetW = (decoded.width * scale).roundToInt().coerceAtLeast(1)
                val targetH = (decoded.height * scale).roundToInt().coerceAtLeast(1)

                val resized = if (scale < 1.0f) {
                    Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                } else {
                    decoded
                }

                val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(resized, 0f, 0f, paint)
                pdfDocument.finishPage(page)

                if (resized != decoded) resized.recycle()
                decoded.recycle()
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            return false
        }
    }
}
