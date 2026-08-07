import re

path = '/app/applet/app/src/main/java/com/example/ui/DocumentViewModel.kt'
with open(path, 'r') as f:
    content = f.read()

pattern = r'val paragraphs = pageText\.split\(Regex\("\\n\\\\s\*\\n"\)\)[\s\S]*?para\.spacingAfter = 200\s*\}\s*\}'

new_code = """val paragraphs = pageText.split(Regex("\\n\\\\s*\\n"))
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
                                    run.fontSize = 11

                                    lines.forEachIndexed { lineIndex, line ->
                                        run.setText(line)
                                        if (lineIndex < lines.size - 1) {
                                            run.addBreak()
                                        }
                                    }
                                    para.spacingAfter = 200
                                }
                            }
                        }"""

if re.search(pattern, content):
    content = re.sub(pattern, new_code.replace('\\', '\\\\'), content, count=1)
    with open(path, 'w') as f:
        f.write(content)
    print("Success")
else:
    print("Failed to find old code block")
