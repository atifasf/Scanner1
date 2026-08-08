package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import android.widget.Toast

object ShareHelper {
    fun shareDocument(context: Context, originalFile: File, documentName: String) {
        val extension = originalFile.extension.ifBlank { "pdf" }
        val cleanName = documentName.trim()
        val safeName = cleanName.replace(Regex("[^\\p{L}\\p{N}.\\-_ ]"), "_").trim()
        val finalName = if (safeName.lowercase().endsWith(".${extension.lowercase()}")) {
            safeName
        } else {
            "$safeName.$extension"
        }
        
        val shareDir = File(context.cacheDir, "shared_docs")
        shareDir.mkdirs()
        
        val tempFile = File(shareDir, finalName)
        try {
            originalFile.copyTo(tempFile, overwrite = true)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = when (extension.lowercase()) {
                    "pdf" -> "application/pdf"
                    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "txt" -> "text/plain"
                    else -> "*/*"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error sharing file", Toast.LENGTH_SHORT).show()
        }
    }
}
