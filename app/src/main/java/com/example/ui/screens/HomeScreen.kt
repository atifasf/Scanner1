package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import java.util.UUID
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.DocumentEntity
import com.example.ui.DocumentViewModel
import com.example.ui.ScannerHelper
import com.example.ui.OCRHelper
import android.net.Uri
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DocumentViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val documents by viewModel.allDocuments.collectAsState()
    val folders by viewModel.allFolders.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var selectedCategory by remember { mutableStateOf("All") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var documentToMove by remember { mutableStateOf<DocumentEntity?>(null) }
    var folderToRename by remember { mutableStateOf<com.example.data.FolderEntity?>(null) }
    var documentToRename by remember { mutableStateOf<DocumentEntity?>(null) }
    var currentTab by remember { mutableStateOf("Home") }

    var isIdCardScan by remember { mutableStateOf(false) }
    var isExtractTextFromCamera by remember { mutableStateOf(false) }
    var isTableScanFromCamera by remember { mutableStateOf(false) }
    
    var showIdCardGuideDialog by remember { mutableStateOf(false) }
    var showExtractTextOptionsDialog by remember { mutableStateOf(false) }
    var showTableScanOptionsDialog by remember { mutableStateOf(false) }
    var successDialogDoc by remember { mutableStateOf<DocumentEntity?>(null) }

    val coroutineScope = rememberCoroutineScope()

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


    if (documentToRename != null) {
        var newDocumentName by remember { mutableStateOf(documentToRename!!.name) }
        AlertDialog(
            onDismissRequest = { documentToRename = null },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = newDocumentName,
                    onValueChange = { newDocumentName = it },
                    label = { Text("New Document Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newDocumentName.isNotBlank()) {
                        viewModel.updateDocument(documentToRename!!.copy(name = newDocumentName))
                        documentToRename = null
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { documentToRename = null }) { Text("Cancel") }
            }
        )
    }

    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotBlank()) {
                        viewModel.createFolder(folderName)
                        showCreateFolderDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (folderToRename != null) {
        var newFolderName by remember { mutableStateOf(folderToRename!!.name) }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text("Rename Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("New Folder Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        viewModel.updateFolder(folderToRename!!.copy(name = newFolderName))
                        folderToRename = null
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) { Text("Cancel") }
            }
        )
    }

    if (documentToMove != null) {
        AlertDialog(
            onDismissRequest = { documentToMove = null },
            title = { Text("Move to Folder") },
            text = {
                if (folders.isEmpty()) {
                    Text("No folders available. Create a folder first.")
                } else {
                    LazyColumn {
                        items(folders) { folder ->
                            ListItem(
                                headlineContent = { Text(folder.name) },
                                modifier = Modifier.clickable {
                                    viewModel.moveDocumentToFolder(documentToMove!!.id, folder.id)
                                    documentToMove = null
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { documentToMove = null }) { Text("Cancel") }
            }
        )
    }

    var scannedImageUris by remember { mutableStateOf<List<android.net.Uri>?>(null) }
    var currentFolderId by remember { mutableStateOf<String?>(null) }
    var showFormatSelectionScreen by remember { mutableStateOf(false) }
    var renameStepActive by remember { mutableStateOf(false) }
    var documentNameInput by remember { mutableStateOf("") }
    var isNameEditedByUser by remember { mutableStateOf(false) }
    
    var editingFile by remember { mutableStateOf<File?>(null) }
    var editingPageIndex by remember { mutableStateOf(-1) }
    var scannedImagesRefreshTrigger by remember { mutableStateOf(0) }

    if (showFormatSelectionScreen && scannedImageUris != null) {
        val ocrProgressState by viewModel.ocrProgress.collectAsState()
        var selectedFormat by remember { mutableStateOf(com.example.ui.DocumentViewModel.OutputFormat.PDF) }
        var isSearchablePdf by remember { mutableStateOf(false) }
        
        val defaultPrefix = when (selectedFormat) {
            com.example.ui.DocumentViewModel.OutputFormat.PDF -> "Scan"
            com.example.ui.DocumentViewModel.OutputFormat.JPEG -> "Scan_Img"
            com.example.ui.DocumentViewModel.OutputFormat.WORD -> "Scan_Doc"
        }
        
        LaunchedEffect(selectedFormat, showFormatSelectionScreen) {
            if (!isNameEditedByUser && showFormatSelectionScreen) {
                documentNameInput = "${defaultPrefix}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}"
            }
        }
        
        LaunchedEffect(showFormatSelectionScreen) {
            if (!showFormatSelectionScreen) {
                renameStepActive = false
                isNameEditedByUser = false
            }
        }
        
        AlertDialog(
            onDismissRequest = { 
                if (ocrProgressState == null) {
                    showFormatSelectionScreen = false 
                    scannedImageUris = null
                    renameStepActive = false
                    isNameEditedByUser = false
                    isIdCardScan = false
                }
            },
            title = {
                Text(
                    text = if (ocrProgressState != null) "Processing..." else if (renameStepActive) "Rename Document" else "Export Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (ocrProgressState != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp
                            )
                            Text(
                                text = ocrProgressState!!,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (renameStepActive) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Give your scanned document a descriptive name so it's easy to find in the list.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            OutlinedTextField(
                                value = documentNameInput,
                                onValueChange = { 
                                    documentNameInput = it
                                    isNameEditedByUser = true
                                },
                                label = { Text("Document Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    if (documentNameInput.isNotEmpty()) {
                                        IconButton(onClick = { 
                                            documentNameInput = "" 
                                            isNameEditedByUser = true
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                                        }
                                    }
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Format Info",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Will be saved as ${selectedFormat.name} format.",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    } else {
                        // Page review and edit section with Eraser Tool
                        Text(
                            text = "Scanned Pages (Tap to edit/erase):",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            scannedImageUris?.forEachIndexed { index, uri ->
                                val file = File(uri.path ?: "")
                                Box(
                                    modifier = Modifier
                                        .size(width = 90.dp, height = 120.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            editingFile = file
                                            editingPageIndex = index
                                        },
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    AsyncImage(
                                        model = remember(file, scannedImagesRefreshTrigger) {
                                            coil.request.ImageRequest.Builder(context)
                                                .data(file)
                                                .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                                                .build()
                                        },
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Gesture,
                                                contentDescription = "Eraser",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "Eraser",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        HorizontalDivider()

                        Text(
                            text = "Choose the file format for your scanned document:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedFormat = com.example.ui.DocumentViewModel.OutputFormat.PDF },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedFormat == com.example.ui.DocumentViewModel.OutputFormat.PDF) 
                                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedFormat == com.example.ui.DocumentViewModel.OutputFormat.PDF,
                                        onClick = { selectedFormat = com.example.ui.DocumentViewModel.OutputFormat.PDF }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "PDF Document (.pdf)",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Best for sharing as a single multi-page file.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedFormat = com.example.ui.DocumentViewModel.OutputFormat.JPEG },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedFormat == com.example.ui.DocumentViewModel.OutputFormat.JPEG) 
                                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedFormat == com.example.ui.DocumentViewModel.OutputFormat.JPEG,
                                        onClick = { selectedFormat = com.example.ui.DocumentViewModel.OutputFormat.JPEG }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "JPEG Images (.jpg)",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Saves each page as an optimized high-quality image.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedFormat = com.example.ui.DocumentViewModel.OutputFormat.WORD },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedFormat == com.example.ui.DocumentViewModel.OutputFormat.WORD) 
                                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedFormat == com.example.ui.DocumentViewModel.OutputFormat.WORD,
                                        onClick = { selectedFormat = com.example.ui.DocumentViewModel.OutputFormat.WORD }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Word Document (.docx)",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Extracts editable text using high-accuracy OCR.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        
                        if (selectedFormat == com.example.ui.DocumentViewModel.OutputFormat.PDF) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isSearchablePdf = !isSearchablePdf }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Searchable PDF (OCR)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Make text in the PDF selectable and searchable.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isSearchablePdf,
                                    onCheckedChange = { isSearchablePdf = it }
                                )
                            }
                        }
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Optimize",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Smart File Size Optimization Active",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (ocrProgressState == null) {
                    if (renameStepActive) {
                        Button(
                            onClick = {
                                viewModel.saveScannedDocumentWithFormat(
                                    imageUris = scannedImageUris!!,
                                    format = selectedFormat,
                                    isSearchablePdf = isSearchablePdf,
                                    customName = documentNameInput,
                                    folderId = currentFolderId,
                                    isIdCardGrid = isIdCardScan
                                ) {
                                    showFormatSelectionScreen = false
                                    scannedImageUris = null
                                    renameStepActive = false
                                    isNameEditedByUser = false
                                    isIdCardScan = false
                                    if (it.name.startsWith("ID_Card_")) { successDialogDoc = it }
                                }
                            },
                            enabled = documentNameInput.isNotBlank()
                        ) {
                            Text("Save & Export")
                        }
                    } else {
                        Button(
                            onClick = {
                                renameStepActive = true
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Next")
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            dismissButton = {
                if (ocrProgressState == null) {
                    if (renameStepActive) {
                        TextButton(
                            onClick = {
                                renameStepActive = false
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Back")
                            }
                        }
                    } else {
                        TextButton(
                            onClick = {
                                showFormatSelectionScreen = false
                                scannedImageUris = null
                                isIdCardScan = false
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        )
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        ScannerHelper.handleScanResult(result) { imageUris, pdfUri ->
            val folderId = if (selectedCategory.startsWith("Folder_")) selectedCategory.removePrefix("Folder_") else null
            if (imageUris.isNotEmpty()) {
                // Copy read-only system URIs to editable local cache files!
                val id = UUID.randomUUID().toString()
                val cachedUris = imageUris.mapIndexed { index, uri ->
                    val file = File(context.cacheDir, "temp_edit_${id}_$index.jpg")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    android.net.Uri.fromFile(file)
                }
                
                scannedImageUris = cachedUris
                currentFolderId = folderId
                
                if (isIdCardScan) {
                    documentNameInput = "ID_Card_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}"
                    isNameEditedByUser = true
                    showFormatSelectionScreen = true
                } else if (isExtractTextFromCamera) {
                    isExtractTextFromCamera = false
                    viewModel.ocrImageUri = cachedUris.firstOrNull()
                    viewModel.ocrIsTableSelected = false
                    
                    coroutineScope.launch {
                        viewModel.ocrProgress.value = "Performing OCR..."
                        val textBuilder = java.lang.StringBuilder()
                        cachedUris.forEachIndexed { index, uri ->
                            viewModel.ocrProgress.value = "Extracting text (Page ${index + 1} of ${cachedUris.size})..."
                            val extractedText = viewModel.extractTextFromUri(context, uri)
                            if (extractedText.isNotBlank()) {
                                textBuilder.append(extractedText).append("\n\n")
                            }
                        }
                        viewModel.ocrExtractedText = textBuilder.toString().trim()
                        viewModel.ocrProgress.value = null
                        currentTab = "OCR"
                    }
                } else if (isTableScanFromCamera) {
                    isTableScanFromCamera = false
                    val firstUri = cachedUris.firstOrNull()
                    viewModel.ocrImageUri = firstUri
                    viewModel.ocrIsTableSelected = true
                    currentTab = "OCR"
                    
                    if (firstUri != null) {
                        coroutineScope.launch {
                            viewModel.ocrProgress.value = "Extracting table..."
                            viewModel.ocrExtractedText = ""
                            OCRHelper.extractTableAsCsv(context, firstUri, onSuccess = { csv ->
                                viewModel.ocrExtractedText = csv
                                viewModel.ocrProgress.value = null
                            }, onError = { err ->
                                viewModel.ocrExtractedText = "Failed to extract table: ${err.message}"
                                viewModel.ocrProgress.value = null
                            })
                        }
                    }
                } else {
                    showFormatSelectionScreen = true
                }
            }
        }
    }

    val galleryImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.ocrImageUri = uri
            viewModel.ocrIsTableSelected = false
            currentTab = "OCR"
            coroutineScope.launch {
                viewModel.ocrProgress.value = "Performing OCR..."
                viewModel.ocrExtractedText = ""
                val extracted = viewModel.extractTextFromUri(context, uri)
                viewModel.ocrExtractedText = extracted
                viewModel.ocrProgress.value = null
            }
        }
    }

    val galleryTablePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.ocrImageUri = uri
            viewModel.ocrIsTableSelected = true
            currentTab = "OCR"
            coroutineScope.launch {
                viewModel.ocrProgress.value = "Extracting table..."
                viewModel.ocrExtractedText = ""
                OCRHelper.extractTableAsCsv(context, uri, onSuccess = { csv ->
                    viewModel.ocrExtractedText = csv
                    viewModel.ocrProgress.value = null
                }, onError = { err ->
                    viewModel.ocrExtractedText = "Failed to extract table: ${err.message}"
                    viewModel.ocrProgress.value = null
                })
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.convertPdfToWord(
                pdfUri = uri,
                onStart = {
                    viewModel.ocrProgress.value = "Opening PDF..."
                },
                onProgress = { status ->
                    viewModel.ocrProgress.value = status
                },
                onSuccess = { name ->
                    viewModel.ocrProgress.value = null
                    Toast.makeText(context, "Successfully converted: $name", Toast.LENGTH_LONG).show()
                },
                onFailure = { error ->
                    viewModel.ocrProgress.value = null
                    Toast.makeText(context, "Error: $error", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    if (showIdCardGuideDialog) {
        AlertDialog(
            onDismissRequest = { showIdCardGuideDialog = false },
            title = { Text("ID Card Scan", fontWeight = FontWeight.Bold) },
            text = {
                Text("Place your ID card/Card on a flat, well-lit surface.\n\n" +
                     "1. First scan the Front side of the card.\n" +
                     "2. Then tap 'Add Page' to scan the Back side.\n\n" +
                     "Both sides will be automatically compiled into a professional document.",
                     style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(onClick = {
                    showIdCardGuideDialog = false
                    isIdCardScan = true
                    val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
                    if (activity != null) {
                        ScannerHelper.startScan(activity, scannerLauncher)
                    }
                }) {
                    Text("Start Scan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIdCardGuideDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExtractTextOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showExtractTextOptionsDialog = false },
            title = { Text("Extract Text (OCR)", fontWeight = FontWeight.Bold) },
            text = {
                Text("Choose whether to scan a printed document with your camera or select an image from your gallery.",
                     style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showExtractTextOptionsDialog = false
                            isExtractTextFromCamera = true
                            val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
                            if (activity != null) {
                                ScannerHelper.startScan(activity, scannerLauncher)
                            }
                        }
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Document (Camera)")
                    }
                    
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showExtractTextOptionsDialog = false
                            galleryImagePicker.launch("image/*")
                        }
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose from Gallery")
                    }
                    
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showExtractTextOptionsDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (showTableScanOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showTableScanOptionsDialog = false },
            title = { Text("Scan Table (Excel)", fontWeight = FontWeight.Bold) },
            text = {
                Text("Scan a spreadsheet/table with your camera or select an image from your gallery to extract structured rows and columns as Excel.",
                     style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showTableScanOptionsDialog = false
                            isTableScanFromCamera = true
                            val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
                            if (activity != null) {
                                ScannerHelper.startScan(activity, scannerLauncher)
                            }
                        }
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Table (Camera)")
                    }
                    
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showTableScanOptionsDialog = false
                            galleryTablePicker.launch("image/*")
                        }
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose from Gallery")
                    }
                    
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showTableScanOptionsDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                com.example.ui.components.BannerAd()
                BottomNavigationBar(currentTab = currentTab, onTabSelected = { currentTab = it }, onNavigateToSettings = onNavigateToSettings)
            }
        },
        floatingActionButton = {
            if (currentTab == "Home") {
                FloatingActionButton(
                    onClick = {
                        val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
                        if (activity != null) {
                            ScannerHelper.startScan(activity, scannerLauncher)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Scan", modifier = Modifier.size(32.dp))
                }
            }
        }
    ) { padding ->
        when (currentTab) {
            "Home" -> {
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "DocScanner",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                // 2x2 Feature Grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // ID Card Scan Button
                        Card(
                            onClick = { showIdCardGuideDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(96.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreditCard,
                                    contentDescription = "ID Card Scan",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "ID Card Scan",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1
                                )
                            }
                        }
                        
                        // Extract Text Button
                        Card(
                            onClick = { showExtractTextOptionsDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(96.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Extract Text",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Extract Text",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // PDF to Word Button
                        Card(
                            onClick = { pdfPickerLauncher.launch("application/pdf") },
                            modifier = Modifier
                                .weight(1f)
                                .height(96.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InsertDriveFile,
                                    contentDescription = "PDF to Word",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "PDF to Word",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 1
                                )
                            }
                        }
                        
                        // Scan Table to Excel Button
                        Card(
                            onClick = { showTableScanOptionsDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(96.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridOn,
                                    contentDescription = "Scan Table",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Scan Table",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Category Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(text = "All", isSelected = selectedCategory == "All") { selectedCategory = "All" }
                CategoryChip(text = "Folders", isSelected = selectedCategory == "Folders") { selectedCategory = "Folders" }
                CategoryChip(text = "PDFs", isSelected = selectedCategory == "PDFs") { selectedCategory = "PDFs" }
                CategoryChip(text = "OCR", isSelected = selectedCategory == "OCR") { selectedCategory = "OCR" }
                CategoryChip(text = "Favorites", isSelected = selectedCategory == "Favorites") { selectedCategory = "Favorites" }
            }

            // Main Content Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val headerText = when {
                    selectedCategory == "Folders" -> "MY FOLDERS"
                    selectedCategory.startsWith("Folder_") -> {
                        val folderId = selectedCategory.removePrefix("Folder_")
                        folders.find { it.id == folderId }?.name?.uppercase() ?: "FOLDER"
                    }
                    else -> "RECENT SCANS"
                }
                Text(
                    text = headerText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                if (selectedCategory == "Folders") {
                    IconButton(onClick = { showCreateFolderDialog = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Create Folder", tint = MaterialTheme.colorScheme.primary)
                    }
                } else if (selectedCategory.startsWith("Folder_")) {
                    IconButton(onClick = { selectedCategory = "Folders" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close Folder", tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Document List
            if (selectedCategory == "Folders") {
                if (folders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                        Text("No folders yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(folders) { folder ->
                            val docCount = documents.count { it.folderId == folder.id }
                            FolderItem(folder = folder, docCount = docCount, onClick = {
                                selectedCategory = "Folder_${folder.id}"
                            }, onRename = { folderToRename = folder }, onDelete = { viewModel.deleteFolder(folder) })
                        }
                    }
                }
            } else {
                val filteredDocs = when {
                    selectedCategory == "PDFs" -> documents.filter { it.pdfPath != null }
                    selectedCategory == "OCR" -> documents.filter { !it.ocrText.isNullOrEmpty() }
                    selectedCategory == "Favorites" -> documents.filter { it.isFavorite }
                    selectedCategory.startsWith("Folder_") -> {
                        val folderId = selectedCategory.removePrefix("Folder_")
                        documents.filter { it.folderId == folderId }
                    }
                    else -> documents
                }
                if (filteredDocs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No documents found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredDocs) { doc ->
                            DocumentListItem(
                                document = doc,
                                onClick = { onNavigateToDetail(doc.id) },
                                onDeleteClick = { viewModel.moveToTrash(doc.id) },
                                onMoveClick = { documentToMove = doc },
                                onRenameClick = { documentToRename = doc },
                                onShareClick = {
                                    val file = doc.pdfPath?.let { java.io.File(it) } 
                                        ?: doc.imagePaths.split(",").firstOrNull()?.let { java.io.File(it) }
                                    if (file != null && file.exists()) {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = if (doc.pdfPath != null) {
                                                if (doc.pdfPath.endsWith(".docx")) "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                                else "application/pdf"
                                            } else "image/jpeg"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Document"))
                                    }
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
            } // end of Column
            } // end of "Home" -> {
            "OCR" -> {
                com.example.ui.screens.OcrTab(viewModel, padding)
            }
            "Tools" -> {
                com.example.ui.screens.ToolsTab(viewModel, padding, context)
            }
        } // end of when
    }

    if (editingFile != null) {
        com.example.ui.components.EraserCanvasEditor(
            imageFile = editingFile!!,
            onSave = {
                scannedImagesRefreshTrigger++
                editingFile = null
                editingPageIndex = -1
                Toast.makeText(context, "Page updated successfully!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                editingFile = null
                editingPageIndex = -1
            }
        )
    }
}

@Composable
fun CategoryChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(24.dp)
            )
            .border(
                1.dp,
                if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun FolderItem(
    folder: com.example.data.FolderEntity,
    docCount: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(Icons.Default.Folder, contentDescription = "Folder", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            Column {
                Text(text = folder.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "$docCount documents", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DocumentListItem(
    document: DocumentEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoveClick: () -> Unit,
    onRenameClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val firstImagePath = document.imagePaths.split(",").firstOrNull()
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!firstImagePath.isNullOrEmpty()) {
                AsyncImage(
                    model = File(firstImagePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("PDF", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = document.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(document.dateCreated))
            val sizeMb = if (document.sizeBytes > 0) String.format(Locale.US, "%.1f MB", document.sizeBytes / 1024.0 / 1024.0) else "Unknown"
            Text(
                text = "$dateStr • $sizeMb",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        // Actions
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        showMenu = false
                        onClick()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        showMenu = false
                        onRenameClick()
                    },
                    leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = {
                        showMenu = false
                        onShareClick()
                    },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Move to Folder") },
                    onClick = {
                        showMenu = false
                        onMoveClick()
                    },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        showMenu = false
                        onDeleteClick()
                    },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(currentTab: String, onTabSelected: (String) -> Unit, onNavigateToSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem(icon = Icons.Default.Home, label = "Home", isSelected = currentTab == "Home", onClick = { onTabSelected("Home") })
        BottomNavItem(icon = Icons.Default.TextFields, label = "OCR", isSelected = currentTab == "OCR", onClick = { onTabSelected("OCR") })
        BottomNavItem(icon = Icons.Default.Build, label = "Tools", isSelected = currentTab == "Tools", onClick = { onTabSelected("Tools") })
        BottomNavItem(
            icon = Icons.Default.Settings,
            label = "Settings",
            isSelected = false,
            onClick = onNavigateToSettings
        )
    }
}

@Composable
fun BottomNavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (label == "OCR") {
                    Text(
                        text = "OCR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (label == "OCR") {
                    Text(
                        text = "OCR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                } else {
                    Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}
