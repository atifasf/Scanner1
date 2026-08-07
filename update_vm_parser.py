import re

path = 'app/src/main/java/com/example/ui/DocumentViewModel.kt'
with open(path, 'r') as f:
    content = f.read()

# Replace the text appending logic inside convertPdfToWord to use ExportHelper.appendContentToDoc
start_str = 'if (!pageText.isNullOrBlank()) {'
end_str = '} else {\n                        val para = doc.createParagraph()'

start_idx = content.find(start_str)
if start_idx != -1:
    end_idx = content.find(end_str, start_idx)
    if end_idx != -1:
        replacement = """if (!pageText.isNullOrBlank()) {
                        extractedTexts.add(pageText)
                        ExportHelper.appendContentToDoc(doc, pageText)
                    """
        content = content[:start_idx] + replacement + content[end_idx:]
        with open(path, 'w') as f:
            f.write(content)
        print("Updated DocumentViewModel convertPdfToWord")
    else:
        print("End string not found in DocumentViewModel")
else:
    print("Start string not found in DocumentViewModel")
