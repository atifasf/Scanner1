import re

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "r") as f:
    code = f.read()

code = code.replace("var showTableScanOptionsDialog by remember { mutableStateOf(false) }", 
                    "var showTableScanOptionsDialog by remember { mutableStateOf(false) }\n    var successDialogDoc by remember { mutableStateOf<DocumentEntity?>(null) }")

code = code.replace("isIdCardScan = false\n                                }", 
                    "isIdCardScan = false\n                                    if (it.name.startsWith(\"ID_Card_\")) { successDialogDoc = it }\n                                }")

dialog_code = """
    if (successDialogDoc != null) {
        val doc = successDialogDoc!!
        AlertDialog(
            onDismissRequest = { successDialogDoc = null },
            title = { Text("Success!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = { Text("ID Card PDF has been created successfully.") },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        successDialogDoc = null
                        val pdfFile = java.io.File(doc.pdfPath ?: "")
                        if (pdfFile.exists()) {
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", pdfFile)
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share PDF"))
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share")
                    }
                    
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        successDialogDoc = null
                        val pdfFile = java.io.File(doc.pdfPath ?: "")
                        if (pdfFile.exists()) {
                            val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager
                            printManager?.let { pm ->
                                val jobName = "Document Print - ${doc.name}"
                                val printAdapter = object : android.print.PrintDocumentAdapter() {
                                    override fun onWrite(pages: Array<out android.print.PageRange>?, destination: android.os.ParcelFileDescriptor?, cancellationSignal: android.os.CancellationSignal?, callback: WriteResultCallback?) {
                                        var inStream: java.io.InputStream? = null
                                        var outStream: java.io.OutputStream? = null
                                        try {
                                            inStream = java.io.FileInputStream(pdfFile)
                                            outStream = java.io.FileOutputStream(destination?.fileDescriptor)
                                            val buf = ByteArray(16384)
                                            var size: Int
                                            while (inStream.read(buf).also { size = it } >= 0 && cancellationSignal?.isCanceled == false) {
                                                outStream.write(buf, 0, size)
                                            }
                                            if (cancellationSignal?.isCanceled == true) { callback?.onWriteCancelled() } else { callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES)) }
                                        } catch (e: Exception) {
                                            callback?.onWriteFailed(e.message)
                                        } finally {
                                            inStream?.close()
                                            outStream?.close()
                                        }
                                    }
                                    override fun onLayout(oldAttributes: android.print.PrintAttributes?, newAttributes: android.print.PrintAttributes?, cancellationSignal: android.os.CancellationSignal?, callback: LayoutResultCallback?, extras: android.os.Bundle?) {
                                        if (cancellationSignal?.isCanceled == true) { callback?.onLayoutCancelled(); return }
                                        val info = android.print.PrintDocumentInfo.Builder(doc.name + ".pdf").setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build()
                                        callback?.onLayoutFinished(info, true)
                                    }
                                }
                                pm.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
                            }
                        }
                    }) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Print")
                    }
                    
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { successDialogDoc = null }) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View in Library")
                    }
                }
            },
            dismissButton = {
                TextButton(modifier = Modifier.fillMaxWidth(), onClick = { successDialogDoc = null }) {
                    Text("Close", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        )
    }
"""

code = code.replace("val coroutineScope = rememberCoroutineScope()", "val coroutineScope = rememberCoroutineScope()\n" + dialog_code)

with open("app/src/main/java/com/example/ui/screens/HomeScreen.kt", "w") as f:
    f.write(code)
