import sys

path = '/app/applet/app/src/main/java/com/example/ui/ExportHelper.kt'
with open(path, 'r') as f:
    content = f.read()

old_code = """    fun exportToWord(context: Context, text: String, fileName: String) {
        try {
            val doc = XWPFDocument()
            val para = doc.createParagraph()
            val run = para.createRun()
            text.split("\\n").forEach {
                run.setText(it)
                run.addBreak()
            }
            val file = File(context.cacheDir, "$fileName.docx")
            FileOutputStream(file).use { doc.write(it) }
            doc.close()
            shareFile(context, file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to export Word", Toast.LENGTH_SHORT).show()
        }
    }"""

new_code = """    fun exportToWord(context: Context, text: String, fileName: String) {
        try {
            val doc = XWPFDocument()
            val paragraphs = text.split(Regex("\\\\n\\\\s*\\\\n"))
            paragraphs.forEach { pText ->
                val trimmedP = pText.trim()
                if (trimmedP.isNotEmpty()) {
                    val lines = trimmedP.split("\\n")
                    val isTable = lines.size > 1 && lines.any { it.contains("|") && it.contains("-") && it.replace(Regex("[\\\\s|\\\\-]"), "").isEmpty() }

                    if (isTable) {
                        val tableLines = lines.filter { it.contains("|") && !it.matches(Regex("^[\\\\s|\\\\-:]+$")) }
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
    }"""

if old_code in content:
    content = content.replace(old_code, new_code)
    with open(path, 'w') as f:
        f.write(content)
    print("Success")
else:
    print("Could not find old code in ExportHelper")
