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
    init {
        System.setProperty("javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
        System.setProperty("javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
        System.setProperty("javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")
    }

    private fun shareFile(context: Context, file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Save or Share File"))
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(context, "Error sharing file", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportToTxt(context: Context, text: String, fileName: String) {
        try {
            val file = File(context.cacheDir, "$fileName.txt")
            FileWriter(file).use { it.write(text) }
            shareFile(context, file, "text/plain")
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to save TXT", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportToWord(context: Context, text: String, fileName: String) {
        try {
            val doc = XWPFDocument()
            val paragraphs = text.split(Regex("\\n\\s*\\n"))
            paragraphs.forEach { pText ->
                val trimmedP = pText.trim()
                if (trimmedP.isNotEmpty()) {
                    val lines = trimmedP.split("\n")
                    val isTable = lines.size > 1 && lines.any { it.contains("|") && it.contains("-") && it.replace(Regex("[\\s|\\-]"), "").isEmpty() }

                    if (isTable) {
                        val tableLines = lines.filter { it.contains("|") && !it.matches(Regex("^[\\s|\\-:]+$")) }
                        if (tableLines.isNotEmpty()) {
                            val parsedRows = tableLines.map { line ->
                                var l = line.trim()
                                if (l.startsWith("|")) l = l.substring(1)
                                if (l.endsWith("|")) l = l.substring(0, l.length - 1)
                                l.split("|").map { it.trim() }
                            }
                            val maxCols = parsedRows.maxOfOrNull { it.size } ?: 1
                            val table = doc.createTable(parsedRows.size, maxCols)
                            parsedRows.forEachIndexed { rIndex, rowData ->
                                val tableRow = table.getRow(rIndex)
                                rowData.forEachIndexed { cIndex, cellData ->
                                    val cell = tableRow?.getCell(cIndex)
                                    if (cell != null) {
                                        if (cell.paragraphs.isNotEmpty()) {
                                            val p = cell.paragraphs[0]
                                            val r = p.createRun()
                                            r.fontSize = 11
                                            r.setText(cellData)
                                        } else {
                                            cell.setText(cellData)
                                        }
                                    }
                                }
                            }
                            val endPara = doc.createParagraph()
                            endPara.spacingAfter = 200
                        }
                    } else {
                        val para = doc.createParagraph()
                        val run = para.createRun()
                        lines.forEach {
                            run.setText(it)
                            run.addBreak()
                        }
                        para.spacingAfter = 200
                    }
                }
            }
            
            val file = File(context.cacheDir, "$fileName.docx")
            FileOutputStream(file).use { doc.write(it) }
            doc.close()
            shareFile(context, file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        } catch (e: Throwable) {
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
                val cols = line.split("\t", "   ", " | ", "|")
                cols.forEachIndexed { colIndex, cellValue ->
                    val cell = row.createCell(colIndex)
                    cell.setCellValue(cellValue.trim())
                }
            }
            val file = File(context.cacheDir, "$fileName.xlsx")
            FileOutputStream(file).use { workbook.write(it) }
            workbook.close()
            shareFile(context, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to export Excel", Toast.LENGTH_SHORT).show()
        }
    }
}
