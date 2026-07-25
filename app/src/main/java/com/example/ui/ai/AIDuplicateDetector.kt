package com.example.ui.ai

import android.graphics.Bitmap
import com.example.data.DocumentEntity
import java.io.File
import android.graphics.BitmapFactory
import kotlin.math.abs

data class DuplicateCheckResult(
    val isDuplicate: Boolean,
    val matchedDocument: DocumentEntity?,
    val similarityPercentage: Int
)

object AIDuplicateDetector {

    /**
     * Computes a 64-bit Difference Hash (dHash) for fast visual similarity comparison.
     */
    fun computeDHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L

        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val p1 = scaled.getPixel(x, y)
                val p2 = scaled.getPixel(x + 1, y)

                val lum1 = (ColorRgb(p1) and 0xFF)
                val lum2 = (ColorRgb(p2) and 0xFF)

                if (lum1 > lum2) {
                    hash = hash or (1L shl (y * 8 + x))
                }
            }
        }
        if (scaled != bitmap) scaled.recycle()
        return hash
    }

    private fun ColorRgb(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b).toInt()
    }

    fun calculateHammingDistance(hash1: Long, hash2: Long): Int {
        return java.lang.Long.bitCount(hash1 xor hash2)
    }

    /**
     * Compares a new bitmap against existing stored documents to detect duplicates.
     */
    fun checkDuplicate(newBitmap: Bitmap, existingDocuments: List<DocumentEntity>): DuplicateCheckResult {
        val newHash = computeDHash(newBitmap)
        var bestMatch: DocumentEntity? = null
        var minDistance = 64

        for (doc in existingDocuments) {
            val imgPath = doc.imagePaths.split(",").firstOrNull { it.isNotEmpty() } ?: continue
            val file = File(imgPath)
            if (!file.exists()) continue

            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val existingBmp = BitmapFactory.decodeFile(imgPath, opts) ?: continue
            val existingHash = computeDHash(existingBmp)
            existingBmp.recycle()

            val dist = calculateHammingDistance(newHash, existingHash)
            if (dist < minDistance) {
                minDistance = dist
                bestMatch = doc
            }
        }

        // Hamming distance <= 10 out of 64 bits corresponds to >84% similarity
        val similarity = ((64 - minDistance) / 64.0f * 100).toInt()
        val isDuplicate = minDistance <= 10

        return DuplicateCheckResult(
            isDuplicate = isDuplicate,
            matchedDocument = if (isDuplicate) bestMatch else null,
            similarityPercentage = similarity
        )
    }
}
