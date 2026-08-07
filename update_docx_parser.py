import re

code_helper = '''
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
import org.apache.poi.xwpf.usermodel.XWPFTable

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
            val cleanText = text.replace(Regex("<[^>]*>"), "")
            val file = File(context.cacheDir, "$fileName.txt")
            FileWriter(file).use { it.write(cleanText) }
            shareFile(context, file, "text/plain")
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to save TXT", Toast.LENGTH_SHORT).show()
        }
    }

    fun appendContentToDoc(doc: XWPFDocument, rawContent: String) {
        // Strip markdown code block wrappers if any
        var content = rawContent.replace(Regex("```html|```markdown|```"), "").trim()

        // Check if content contains HTML table tags
        if (content.contains("<table", ignoreCase = true)) {
            parseHtmlToDoc(doc, content)
        } else if (content.contains("|") && content.contains("-")) {
            parseMarkdownToDoc(doc, content)
        } else {
            parsePlainTextToDoc(doc, content)
        }
    }

    private fun parseHtmlToDoc(doc: XWPFDocument, htmlText: String) {
        // Split by table blocks
        val tableRegex = Regex("(?i)<table[^>]*>([\\s\\S]*?)</table>")
        var lastIdx = 0

        for (match in tableRegex.findAll(htmlText)) {
            val beforeTable = htmlText.substring(lastIdx, match.range.first).trim()
            if (beforeTable.isNotEmpty()) {
                parsePlainTextToDoc(doc, beforeTable)
            }

            val tableHtml = match.groupValues[1]
            val rowRegex = Regex("(?i)<tr[^>]*>([\\s\\S]*?)</tr>")
            val cellRegex = Regex("(?i)<t[dh][^>]*>([\\s\\S]*?)</t[dh]>")

            val rowsData = mutableListOf<List<String>>()
            for (rowMatch in rowRegex.findAll(tableHtml)) {
                val rowHtml = rowMatch.groupValues[1]
                val cellsData = cellRegex.findAll(rowHtml).map { cellMatch ->
                    cellMatch.groupValues[1]
                        .replace(Regex("(?i)<p[^>]*>"), "")
                        .replace(Regex("(?i)</p>"), "\n")
                        .replace(Regex("(?i)<br\\s*/?>"), "\n")
                        .replace(Regex("<[^>]*>"), "")
                        .trim()
                }.toList()
                if (cellsData.isNotEmpty()) {
                    rowsData.add(cellsData)
                }
            }

            if (rowsData.isNotEmpty()) {
                val maxCols = rowsData.maxOfOrNull { it.size } ?: 1
                val table = doc.createTable(rowsData.size, maxCols)
                rowsData.forEachIndexed { rIdx, row ->
                    val tableRow = table.getRow(rIdx)
                    row.forEachIndexed { cIdx, cellText ->
                        val cell = tableRow?.getCell(cIdx)
                        if (cell != null) {
                            val lines = cellText.split("\n")
                            if (cell.paragraphs.isNotEmpty()) {
                                val p = cell.paragraphs[0]
                                p.runs.forEach { it.setText("", 0) }
                                val r = p.createRun()
                                r.fontSize = 10
                                lines.forEachIndexed { lIdx, l ->
                                    r.setText(l)
                                    if (lIdx < lines.size - 1) r.addBreak()
                                }
                            } else {
                                cell.setText(cellText)
                            }
                        }
                    }
                }
                val endP = doc.createParagraph()
                endP.spacingAfter = 200
            }

            lastIdx = match.range.last + 1
        }

        if (lastIdx < htmlText.length) {
            val remaining = htmlText.substring(lastIdx).trim()
            if (remaining.isNotEmpty()) {
                parsePlainTextToDoc(doc, remaining)
            }
        }
    }

    private fun parseMarkdownToDoc(doc: XWPFDocument, mdText: String) {
        val paragraphs = mdText.split(Regex("\\n\\s*\\n"))
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
                                        r.fontSize = 10
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
                    parsePlainTextToDoc(doc, trimmedP)
                }
            }
        }
    }

    private fun parsePlainTextToDoc(doc: XWPFDocument, text: String) {
        val cleanText = text.replace(Regex("(?i)<p[^>]*>"), "")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]*>"), "")
            .trim()

        val paragraphs = cleanText.split(Regex("\\n\\s*\\n"))
        paragraphs.forEach { p ->
            val trimmed = p.trim()
            if (trimmed.isNotEmpty()) {
                val para = doc.createParagraph()
                val run = para.createRun()
                run.fontSize = 11
                val lines = trimmed.split("\n")
                lines.forEachIndexed { lIndex, line ->
                    run.setText(line)
                    if (lIndex < lines.size - 1) {
                        run.addBreak()
                    }
                }
                para.spacingAfter = 200
            }
        }
    }

    fun exportToWord(context: Context, text: String, fileName: String) {
        try {
            val doc = XWPFDocument()
            appendContentToDoc(doc, text)
            
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
            val cleanText = text.replace(Regex("<[^>]*>"), "")
            val lines = cleanText.split("\n")
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
'''

with open('app/src/main/java/com/example/ui/ExportHelper.kt', 'w') as f:
    f.write(code_helper)

print("ExportHelper updated successfully")
