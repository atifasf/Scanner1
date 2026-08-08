package com.example.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.ui.ai.AIPdfPasswordProtection
import java.io.File

object ShareHelper {
    fun shareDocument(context: Context, originalFile: File, documentName: String) {
        if (!originalFile.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        var sourceFile = originalFile

        var ext = sourceFile.extension.ifBlank { "pdf" }.lowercase()
        val cleanName = documentName.trim()
        var safeName = cleanName.replace(Regex("[^\\p{L}\\p{N}.\\-_ ]"), "_").trim()
        if (safeName.isBlank()) safeName = "Document"

        val shareDir = File(context.cacheDir, "shared_docs")
        if (!shareDir.exists()) shareDir.mkdirs()

        var finalFileToShare = sourceFile

        // If source file is an image, convert to a real PDF document so external apps receive a valid PDF
        if (ext in listOf("jpg", "jpeg", "png", "bmp", "webp")) {
            val generatedPdf = File(shareDir, "${safeName.removeSuffix(".$ext")}.pdf")
            try {
                val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
                if (bitmap != null) {
                    val pdfDoc = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                    val page = pdfDoc.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDoc.finishPage(page)
                    pdfDoc.writeTo(generatedPdf.outputStream())
                    pdfDoc.close()
                    bitmap.recycle()
                    finalFileToShare = generatedPdf
                    ext = "pdf"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val finalFileName = if (safeName.lowercase().endsWith(".${ext}")) {
            safeName
        } else {
            "$safeName.$ext"
        }

        val tempFile = File(shareDir, finalFileName)
        try {
            finalFileToShare.copyTo(tempFile, overwrite = true)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
            
            val mimeType = when (ext) {
                "pdf" -> "application/pdf"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "txt" -> "text/plain"
                else -> "application/pdf"
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Document", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Share Document").apply {
                clipData = ClipData.newRawUri("Document", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error sharing file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

