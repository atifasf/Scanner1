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
import com.example.ui.ScannerHelper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.util.UUID
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
    var signatureExtractBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var signatureManagerFile by remember { mutableStateOf<File?>(null) }
    var imageRefreshTrigger by remember { mutableStateOf(0) }
    var showAddOptionsDialog by remember { mutableStateOf(false) }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<android.net.Uri> ->
        if (uris.isNotEmpty()) {
            val id = UUID.randomUUID().toString()
            val newImagePaths = mutableListOf<String>()
            uris.forEachIndexed { index, uri ->
                val file = File(context.cacheDir, "temp_append_gal_${id}_$index.jpg")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    newImagePaths.add(file.absolutePath)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (newImagePaths.isNotEmpty()) {
                coroutineScope.launch {
                    viewModel.addImagesToDocument(documentId, newImagePaths)
                    document = viewModel.getDocumentById(documentId)
                    imageRefreshTrigger++
                }
            }
        }
    }

    
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        ScannerHelper.handleScanResult(result) { imageUris, pdfUri ->
            if (imageUris.isNotEmpty()) {
                val id = UUID.randomUUID().toString()
                val newImagePaths = mutableListOf<String>()
                imageUris.forEachIndexed { index, uri ->
                    val file = File(context.cacheDir, "temp_append_${id}_$index.jpg")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        newImagePaths.add(file.absolutePath)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (newImagePaths.isNotEmpty()) {
                    coroutineScope.launch {
                        viewModel.addImagesToDocument(documentId, newImagePaths)
                        document = viewModel.getDocumentById(documentId)
                        imageRefreshTrigger++
                    }
                }
            }
        }
    }

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
                            val originalFile = it.pdfPath?.let { path -> File(path) } 
                                ?: it.imagePaths.split(",").firstOrNull()?.let { path -> File(path) }
                            if (originalFile != null && originalFile.exists()) {
                                com.example.ui.ShareHelper.shareDocument(context, originalFile, it.name)
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
                    imagePaths.forEachIndexed { index, path ->
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            AsyncImage(
                                model = remember(path, imageRefreshTrigger) {
                                    coil.request.ImageRequest.Builder(context)
                                        .data(File(path))
                                        .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                                        .build()
                                },
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        signatureManagerFile = File(path)
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Gesture,
                                        contentDescription = "Signature Tools",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Signature")
                                }

                                FilledIconButton(
                                    onClick = {
                                        editingFile = File(path)
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = "Erase & Clean"
                                    )
                                }
                            }
                        }
                    }
                    
                    // Add Picture Button at the end
                    Button(
                        onClick = { showAddOptionsDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add Page")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Page")
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


    if (signatureManagerFile != null) {
        com.example.ui.components.SignatureManagerDialog(
            documentFile = signatureManagerFile!!,
            onDismiss = { signatureManagerFile = null },
            onDocumentUpdated = {
                imageRefreshTrigger++
            }
        )
    }

    if (signatureExtractBitmap != null) {
        com.example.ui.components.SignatureExtractionDialog(
            documentBitmap = signatureExtractBitmap!!,
            onDismiss = { signatureExtractBitmap = null }
        )
    }

    if (showAddOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showAddOptionsDialog = false },
            title = { Text("Add Page", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = { Text("How would you like to add a new page?") },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showAddOptionsDialog = false
                            val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()
                            if (activity != null) {
                                ScannerHelper.startScan(activity, scannerLauncher)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan with Camera")
                    }
                    Button(
                        onClick = {
                            showAddOptionsDialog = false
                            galleryPickerLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select from Gallery")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddOptionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
