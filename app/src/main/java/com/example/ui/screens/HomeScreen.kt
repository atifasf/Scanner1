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
                                    folderId = currentFolderId
                                ) {
                                    showFormatSelectionScreen = false
                                    scannedImageUris = null
                                    renameStepActive = false
                                    isNameEditedByUser = false
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
                showFormatSelectionScreen = true
            }
        }
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
                    Icon(Icons.Default.Add, contentDescription = "Scan", modifier = Modifier.size(32.dp))
                }
            }
        }
    ) { padding ->
        when (currentTab) {
            "Home" -> {
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search documents",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "SV",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
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
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
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
