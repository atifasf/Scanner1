package com.example.ui

import android.content.Context
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextBox

object ExportHelper {

    fun exportToTxt(context: Context, text: String, fileName: String) {
        try {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "$fileName.txt")
            FileWriter(file).use { it.write(text) }
            Toast.makeText(context, "Saved as TXT: ${file.absolutePath}", Toast.LENGTH_LONG).show()
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
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "$fileName.docx")
            FileOutputStream(file).use { doc.write(it) }
            doc.close()
            Toast.makeText(context, "Exported as Word: ${file.absolutePath}", Toast.LENGTH_LONG).show()
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
                // Split by common separators to simulate table
                val cols = line.split("\t", "   ", " | ")
                cols.forEachIndexed { colIndex, cellValue ->
                    val cell = row.createCell(colIndex)
                    cell.setCellValue(cellValue.trim())
                }
            }
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "$fileName.xlsx")
            FileOutputStream(file).use { workbook.write(it) }
            workbook.close()
            Toast.makeText(context, "Exported as Excel: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to export Excel", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportToPowerPoint(context: Context, text: String, fileName: String) {
        try {
            val ppt = XMLSlideShow()
            val slide = ppt.createSlide()
            val titleShape = slide.createTextBox()
            titleShape.text = "Extracted Text"
            
            val contentShape = slide.createTextBox()
            contentShape.text = text

            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "$fileName.pptx")
            FileOutputStream(file).use { ppt.write(it) }
            ppt.close()
            Toast.makeText(context, "Exported as PowerPoint: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to export PowerPoint", Toast.LENGTH_SHORT).show()
        }
    }
}
