package com.example.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument

object ExportHelper {

    private fun shareFile(context: Context, file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Save or Share File"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error sharing file", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportToTxt(context: Context, text: String, fileName: String) {
        try {
            val file = File(context.cacheDir, "$fileName.txt")
            FileWriter(file).use { it.write(text) }
            shareFile(context, file, "text/plain")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to save TXT", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportToWord(context: Context, text: String, fileName: String) {
        try {
            val doc = XWPFDocument()
            val para = doc.createParagraph()
            val run = para.createRun()
            text.split("\n").forEach {
                run.setText(it)
                run.addBreak()
            }
            val file = File(context.cacheDir, "$fileName.docx")
            FileOutputStream(file).use { doc.write(it) }
            doc.close()
            shareFile(context, file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to export Word", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportToExcel(context: Context, text: String, fileName: String) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Extracted Data")
            val lines = text.split("\n")
            lines.forEachIndexed { rowIndex, line ->
                val row = sheet.createRow(rowIndex)
                val cols = line.split("\t", "   ", " | ")
                cols.forEachIndexed { colIndex, cellValue ->
                    val cell = row.createCell(colIndex)
                    cell.setCellValue(cellValue.trim())
                }
            }
            val file = File(context.cacheDir, "$fileName.xlsx")
            FileOutputStream(file).use { workbook.write(it) }
            workbook.close()
            shareFile(context, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to export Excel", Toast.LENGTH_SHORT).show()
        }
    }
}
