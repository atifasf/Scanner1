package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.DocumentEntity
import com.example.ui.DocumentViewModel
import com.example.ui.OCRHelper
import kotlinx.coroutines.launch
import java.io.File

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.widget.Toast
import com.example.ui.ExportHelper
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    documentId: String,
    viewModel: DocumentViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    var document by remember { mutableStateOf<DocumentEntity?>(null) }
    var isOcrLoading by remember { mutableStateOf(false) }
    var isTableScanLoading by remember { mutableStateOf(false) }
    var editingFile by remember { mutableStateOf<File?>(null) }
    var imageRefreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(documentId) {
        document = viewModel.getDocumentById(documentId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document?.name ?: "Document") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        document?.let {
                            viewModel.toggleFavorite(it)
                            document = it.copy(isFavorite = !it.isFavorite)
                        }
                    }) {
                        Icon(
                            if (document?.isFavorite == true) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite"
                        )
                    }
                    IconButton(onClick = {
                        document?.let {
                            val file = it.pdfPath?.let { path -> File(path) } 
                                ?: it.imagePaths.split(",").firstOrNull()?.let { path -> File(path) }
                            if (file != null && file.exists()) {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (it.pdfPath != null) {
                                        if (it.pdfPath.endsWith(".docx")) "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                        else "application/pdf"
                                    } else "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { padding ->
        if (document == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                val imagePaths = document!!.imagePaths.split(",").filter { it.isNotEmpty() }
                if (imagePaths.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        AsyncImage(
                            model = remember(imagePaths, imageRefreshTrigger) {
                                coil.request.ImageRequest.Builder(context)
                                    .data(File(imagePaths.first()))
                                    .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                                    .build()
                            },
                            contentDescription = "Document preview",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                        )
                        
                        FilledIconButton(
                            onClick = {
                                editingFile = File(imagePaths.first())
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gesture,
                                contentDescription = "Erase & Clean"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    Button(
                        onClick = {
                            if (imagePaths.isNotEmpty()) {
                                isOcrLoading = true
                                val uri = android.net.Uri.fromFile(File(imagePaths.first()))
                                val lang = sharedPrefs.getString("ocr_language", "en") ?: "en"
                                OCRHelper.extractText(context, uri, languageCode = lang,
                                    onSuccess = { text ->
                                        isOcrLoading = false
                                        val updatedDoc = document!!.copy(ocrText = text)
                                        viewModel.updateDocument(updatedDoc)
                                        document = updatedDoc
                                    },
                                    onError = {
                                        isOcrLoading = false
                                    }
                                )
                            }
                        },
                        enabled = !isOcrLoading && !isTableScanLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isOcrLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.TextFields, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extract Text")
                        }
                    }

                    Button(
                        onClick = {
                            if (imagePaths.isNotEmpty()) {
                                isTableScanLoading = true
                                val uri = android.net.Uri.fromFile(File(imagePaths.first()))
                                OCRHelper.extractTableAsCsv(context, uri,
                                    onSuccess = { csvText ->
                                        isTableScanLoading = false
                                        val updatedDoc = document!!.copy(ocrText = csvText)
                                        viewModel.updateDocument(updatedDoc)
                                        document = updatedDoc
                                        Toast.makeText(context, "Table extracted!", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = {
                                        isTableScanLoading = false
                                        Toast.makeText(context, "Table scan failed", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        enabled = !isOcrLoading && !isTableScanLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTableScanLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.GridOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Table")
                        }
                    }
                }

                if (!document!!.ocrText.isNullOrEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Extracted Text", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            var editableText by remember(document!!.ocrText) { mutableStateOf(document!!.ocrText!!) }
                            
                            OutlinedTextField(
                                value = editableText,
                                onValueChange = { editableText = it },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 300.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (editableText != document!!.ocrText) {
                                Button(onClick = {
                                    val updatedDoc = document!!.copy(ocrText = editableText)
                                    viewModel.updateDocument(updatedDoc)
                                    document = updatedDoc
                                }, modifier = Modifier.align(Alignment.End)) {
                                    Text("Save Edits")
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            val extractedText = editableText
                            val docName = document!!.name
                            
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Copied Text", extractedText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Text copied successfully.", Toast.LENGTH_SHORT).show()
                                    }, modifier = Modifier.weight(1f)) { Text("Copy Text") }
                                    
                                    OutlinedButton(onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, extractedText)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Text"))
                                    }, modifier = Modifier.weight(1f)) { Text("Share") }
                                }
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        ExportHelper.exportToTxt(context, extractedText, docName)
                                    }, modifier = Modifier.weight(1f)) { Text("Save TXT") }
                                    
                                    OutlinedButton(onClick = {
                                        ExportHelper.exportToWord(context, extractedText, docName)
                                    }, modifier = Modifier.weight(1f)) { Text("Export Word") }
                                }
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        ExportHelper.exportToExcel(context, extractedText, docName)
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Export Excel") }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f, fill = false))
                com.example.ui.components.BannerAd()
            }
        }
    }

    if (editingFile != null) {
        com.example.ui.components.EraserCanvasEditor(
            imageFile = editingFile!!,
            onSave = {
                imageRefreshTrigger++
                editingFile = null
                Toast.makeText(context, "Saved changes successfully!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                editingFile = null
            }
        )
    }
}
