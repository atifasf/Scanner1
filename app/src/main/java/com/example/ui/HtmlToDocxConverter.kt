package com.example.ui

import org.apache.poi.xwpf.usermodel.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge
import java.math.BigInteger

data class RunFormat(
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false,
    var fontSize: Int? = null,
    var fontFamily: String? = null
)

object HtmlToDocxConverter {

    fun appendHtml(doc: XWPFDocument, html: String) {
        val parsed = Jsoup.parseBodyFragment(html)
        val body = parsed.body()
        processElement(body, doc, null, null)
    }

    private fun processElement(element: Element, doc: XWPFDocument, currentPara: XWPFParagraph?, currentRunFormat: RunFormat?) {
        for (node in element.childNodes()) {
            if (node is TextNode) {
                val text = node.text()
                if (text.isNotEmpty()) {
                    val para = currentPara ?: doc.createParagraph()
                    val run = para.createRun()
                    run.setText(text.replace("\n", ""))
                    applyFormat(run, currentRunFormat)
                }
            } else if (node is Element) {
                val newFormat = currentRunFormat?.copy() ?: RunFormat()
                applyCssToFormat(node, newFormat)
                
                val tag = node.tagName().lowercase()
                when (tag) {
                    "p", "h1", "h2", "h3", "h4", "h5", "h6", "div", "li" -> {
                        val para = doc.createParagraph()
                        applyAlignment(para, node)
                        processElement(node, doc, para, newFormat)
                    }
                    "b", "strong" -> {
                        newFormat.bold = true
                        processElement(node, doc, currentPara, newFormat)
                    }
                    "i", "em" -> {
                        newFormat.italic = true
                        processElement(node, doc, currentPara, newFormat)
                    }
                    "u" -> {
                        newFormat.underline = true
                        processElement(node, doc, currentPara, newFormat)
                    }
                    "br" -> {
                        currentPara?.createRun()?.addBreak()
                    }
                    "table" -> {
                        processTable(node, doc, newFormat)
                        // Add empty paragraph after table
                        doc.createParagraph()
                    }
                    "img" -> {
                        // Ignore or add placeholder
                        val alt = node.attr("alt")
                        val para = currentPara ?: doc.createParagraph()
                        val run = para.createRun()
                        run.setText("[Image: ${if (alt.isNotEmpty()) alt else "placeholder"}]")
                        run.setColor("888888")
                        run.isItalic = true
                    }
                    else -> {
                        processElement(node, doc, currentPara, newFormat)
                    }
                }
            }
        }
    }

    private fun applyCssToFormat(element: Element, format: RunFormat) {
        val style = element.attr("style")
        if (style.isNotEmpty()) {
            val rules = style.split(";")
            for (rule in rules) {
                val parts = rule.split(":")
                if (parts.size == 2) {
                    val key = parts[0].trim().lowercase()
                    val value = parts[1].trim().lowercase()
                    if (key == "font-weight" && (value == "bold" || value.toIntOrNull() ?: 0 >= 700)) {
                        format.bold = true
                    }
                    if (key == "font-style" && value == "italic") {
                        format.italic = true
                    }
                    if (key == "text-decoration" && value == "underline") {
                        format.underline = true
                    }
                    if (key == "font-size") {
                        val size = value.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
                        if (size != null) {
                            format.fontSize = size.toInt()
                        }
                    }
                }
            }
        }
    }

    private fun applyAlignment(para: XWPFParagraph, element: Element) {
        val style = element.attr("style")
        val align = element.attr("align").lowercase()
        var textAlign = align
        
        if (style.contains("text-align")) {
            val match = Regex("text-align:\\s*(left|center|right|justify)").find(style)
            if (match != null) {
                textAlign = match.groupValues[1]
            }
        }
        
        when (textAlign) {
            "center" -> para.alignment = ParagraphAlignment.CENTER
            "right" -> para.alignment = ParagraphAlignment.RIGHT
            "justify" -> para.alignment = ParagraphAlignment.BOTH
            "left" -> para.alignment = ParagraphAlignment.LEFT
        }
    }

    private fun applyFormat(run: XWPFRun, format: RunFormat?) {
        if (format == null) return
        if (format.bold) run.isBold = true
        if (format.italic) run.isItalic = true
        if (format.underline) run.underline = UnderlinePatterns.SINGLE
        if (format.fontSize != null && format.fontSize!! > 0) run.fontSize = format.fontSize!!
        if (format.fontFamily != null) run.fontFamily = format.fontFamily
    }

    private fun processTable(tableElement: Element, doc: XWPFDocument, format: RunFormat?) {
        val trElements = tableElement.select("tr")
        if (trElements.isEmpty()) return

        // Calculate grid size
        var maxCols = 0
        for (tr in trElements) {
            val tds = tr.select("td, th")
            var cols = 0
            for (td in tds) {
                val colspan = td.attr("colspan").toIntOrNull() ?: 1
                cols += colspan
            }
            if (cols > maxCols) maxCols = cols
        }
        
        if (maxCols == 0) return

        val table = doc.createTable(trElements.size, maxCols)
        // table.width = "100%"
        
        // We need to keep track of rowspans to skip cells correctly
        val rowSpans = Array(trElements.size) { IntArray(maxCols) }
        
        for (r in trElements.indices) {
            val tr = trElements[r]
            val tds = tr.select("td, th")
            var c = 0
            var tdIndex = 0
            
            val tableRow = table.getRow(r)
            
            while (c < maxCols && tdIndex < tds.size) {
                // Skip if cell is covered by rowspan from above
                while (c < maxCols && rowSpans[r][c] > 0) {
                    c++
                }
                
                if (c >= maxCols || tdIndex >= tds.size) break
                
                val td = tds[tdIndex++]
                val cell = tableRow.getCell(c) ?: tableRow.createCell()
                
                val colspan = td.attr("colspan").toIntOrNull() ?: 1
                val rowspan = td.attr("rowspan").toIntOrNull() ?: 1
                
                // Parse content into the cell
                if (cell.paragraphs.isNotEmpty()) {
                    cell.removeParagraph(0)
                }
                processElementIntoCell(td, cell, format)
                
                // Apply merges
                val tcPr = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
                if (colspan > 1) {
                    val gridSpan = tcPr.gridSpan ?: tcPr.addNewGridSpan()
                    gridSpan.`val` = BigInteger.valueOf(colspan.toLong())
                }
                if (rowspan > 1) {
                    val vMerge = tcPr.vMerge ?: tcPr.addNewVMerge()
                    vMerge.`val` = STMerge.RESTART
                    
                    // Mark subsequent rows
                    for (i in 1 until rowspan) {
                        if (r + i < trElements.size) {
                            for (j in 0 until colspan) {
                                if (c + j < maxCols) {
                                    rowSpans[r + i][c + j] = 1
                                    val rRow = table.getRow(r + i) ?: table.createRow()
                                    val rCell = rRow.getCell(c + j) ?: rRow.createCell()
                                    val rTcPr = rCell.ctTc.tcPr ?: rCell.ctTc.addNewTcPr()
                                    val rVMerge = rTcPr.vMerge ?: rTcPr.addNewVMerge()
                                    rVMerge.`val` = STMerge.CONTINUE
                                }
                            }
                        }
                    }
                }
                
                c += colspan
            }
        }
    }

    private fun processElementIntoCell(element: Element, cell: XWPFTableCell, currentRunFormat: RunFormat?) {
        val dummyDoc = XWPFDocument()
        val p = dummyDoc.createParagraph()
        processElement(element, dummyDoc, p, currentRunFormat)
        
        for (para in dummyDoc.paragraphs) {
            val cellPara = cell.addParagraph()
            cellPara.alignment = para.alignment
            for (run in para.runs) {
                val cellRun = cellPara.createRun()
                cellRun.setText(run.text())
                cellRun.isBold = run.isBold
                cellRun.isItalic = run.isItalic
                if (run.underline != UnderlinePatterns.NONE) cellRun.underline = run.underline
                if (run.fontSize > 0) cellRun.fontSize = run.fontSize
                if (run.color != null) cellRun.setColor(run.color)
            }
        }
        // Remove empty paragraph that was added automatically if there are other paragraphs
        if (cell.paragraphs.size > 1 && cell.paragraphs[0].runs.isEmpty()) {
            cell.removeParagraph(0)
        }
    }
}
