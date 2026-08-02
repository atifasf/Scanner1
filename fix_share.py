import re

with open("app/src/main/java/com/example/ui/screens/DocumentDetailScreen.kt", "r") as f:
    code = f.read()

old_share = """                            val file = it.pdfPath?.let { path -> File(path) } 
                                ?: it.imagePaths.split(",").firstOrNull()?.let { path -> File(path) }
                            if (file != null && file.exists()) {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {"""

new_share = """                            val originalFile = it.pdfPath?.let { path -> File(path) } 
                                ?: it.imagePaths.split(",").firstOrNull()?.let { path -> File(path) }
                            if (originalFile != null && originalFile.exists()) {
                                val extension = originalFile.extension
                                val safeName = it.name.replace(Regex("[^a-zA-Z0-9.-]"), "_") + "." + extension
                                val shareDir = File(context.cacheDir, "share_cache")
                                if (!shareDir.exists()) shareDir.mkdirs()
                                val shareFile = File(shareDir, safeName)
                                originalFile.copyTo(shareFile, overwrite = true)
                                
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", shareFile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {"""

code = code.replace(old_share, new_share)

with open("app/src/main/java/com/example/ui/screens/DocumentDetailScreen.kt", "w") as f:
    f.write(code)
