package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.DocumentViewModel
import com.example.data.DocumentEntity
import com.example.BuildConfig
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    viewModel: DocumentViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val allDocs by viewModel.allDocuments.collectAsState()
    val favoriteDocs by viewModel.favoriteDocuments.collectAsState()
    val trashDocs by viewModel.trashDocuments.collectAsState()
    val folders by viewModel.allFolders.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) }
    
    // Dialog States
    var selectedDocForInspector by remember { mutableStateOf<DocumentEntity?>(null) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Panel") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // M3 TabRow to switch between Dashboard, Database Browser, and Settings/Tools
            TabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Dashboard") },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("DB Browser") },
                    icon = { Icon(Icons.Default.Storage, contentDescription = "DB Browser") }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Diagnostics") },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Diagnostics") }
                )
            }

            when (activeTab) {
                0 -> {
                    // TAB 0: Dashboard overview
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "ScanVerse System Overview",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Stats Grid (Two columns)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Icon(Icons.Default.Description, contentDescription = "Docs", tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Total Documents", style = MaterialTheme.typography.titleSmall)
                                    Text("${allDocs.size}", style = MaterialTheme.typography.headlineMedium)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Icon(Icons.Default.Favorite, contentDescription = "Favorites", tint = Color.Red)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Favorites", style = MaterialTheme.typography.titleSmall)
                                    Text("${favoriteDocs.size}", style = MaterialTheme.typography.headlineMedium)
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Trash", tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("In Trash", style = MaterialTheme.typography.titleSmall)
                                    Text("${trashDocs.size}", style = MaterialTheme.typography.headlineMedium)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                val totalBytes = allDocs.sumOf { it.sizeBytes }
                                val df = DecimalFormat("#.##")
                                val sizeStr = when {
                                    totalBytes < 1024 -> "$totalBytes B"
                                    totalBytes < 1024 * 1024 -> "${df.format(totalBytes / 1024.0)} KB"
                                    else -> "${df.format(totalBytes / (1024.0 * 1024.0))} MB"
                                }
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Icon(Icons.Default.Save, contentDescription = "Storage", tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Total Storage", style = MaterialTheme.typography.titleSmall)
                                    Text(sizeStr, style = MaterialTheme.typography.headlineMedium)
                                }
                            }
                        }

                        // Folder breakdown
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = "Folders", tint = MaterialTheme.colorScheme.primary)
                                    Text("Active Folders", style = MaterialTheme.typography.titleMedium)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                if (folders.isEmpty()) {
                                    Text("No folders created yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                } else {
                                    folders.forEach { folder ->
                                        val docCount = allDocs.count { it.folderId == folder.id }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                                            Badge { Text("$docCount docs") }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // TAB 1: Database Raw Inspector (DB Browser)
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (allDocs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No documents in database.", style = MaterialTheme.typography.bodyLarge)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(allDocs) { doc ->
                                    ListItem(
                                        headlineContent = { Text(doc.name) },
                                        supportingContent = {
                                            val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(doc.dateCreated))
                                            Text("ID: ${doc.id.take(8)}... | Created: $dateStr")
                                        },
                                        leadingContent = {
                                            Icon(
                                                imageVector = if (doc.isTrash) Icons.Default.DeleteSweep else Icons.Default.InsertDriveFile,
                                                contentDescription = "Doc",
                                                tint = if (doc.isTrash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        trailingContent = {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (doc.ocrText != null) {
                                                    Icon(Icons.Default.Translate, contentDescription = "Has OCR Text", tint = Color(0xFF4CAF50))
                                                }
                                                if (doc.pdfPath != null) {
                                                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Has PDF", tint = Color(0xFFFF5722))
                                                }
                                                IconButton(onClick = { selectedDocForInspector = doc }) {
                                                    Icon(Icons.Default.Info, contentDescription = "Inspect Raw")
                                                }
                                            }
                                        },
                                        modifier = Modifier.clickable { selectedDocForInspector = doc }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // TAB 2: Diagnostics & Administration Tools
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Developer & Maintenance Utilities", style = MaterialTheme.typography.titleLarge)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        // Insert 3 beautiful Mock entries to populate database
                                        coroutineScope.launch {
                                            val invoiceId = UUID.randomUUID().toString()
                                            val invoiceDoc = DocumentEntity(
                                                id = invoiceId,
                                                name = "Mock_Invoice_Scan_0823",
                                                dateCreated = System.currentTimeMillis() - 86400000, // 1 day ago
                                                imagePaths = "",
                                                pdfPath = null,
                                                ocrText = "INVOICE #92039\nDate: 23 Aug 2026\nItem: High-performance Scanner\nTotal: $120.00\nPayment: Paid via Card",
                                                sizeBytes = 23904L
                                            )
                                            viewModel.updateDocument(invoiceDoc)

                                            val notesId = UUID.randomUUID().toString()
                                            val notesDoc = DocumentEntity(
                                                id = notesId,
                                                name = "Mock_Urdu_Poetry_Page",
                                                dateCreated = System.currentTimeMillis() - 172800000, // 2 days ago
                                                imagePaths = "",
                                                pdfPath = null,
                                                ocrText = "یوں تو سب کہہ رہے ہیں خیر سے ہے\nپر یہ دل ہی تو ہے جو ڈرتا ہے\nعشق کی انتہا نہیں ہوتی\nحسن تو ہر جگہ بکھرتا ہے",
                                                sizeBytes = 48500L
                                            )
                                            viewModel.updateDocument(notesDoc)

                                            val reportId = UUID.randomUUID().toString()
                                            val reportDoc = DocumentEntity(
                                                id = reportId,
                                                name = "Mock_AI_Strategy_Draft",
                                                dateCreated = System.currentTimeMillis() - 3600000, // 1 hour ago
                                                imagePaths = "",
                                                pdfPath = null,
                                                ocrText = "ScanVerse AI Document Core Strategy\n1. Use robust Jetpack Compose frontend\n2. Integrate Room local persistence\n3. Leverage multimodal Gemini Flash for non-Latin script OCR.",
                                                sizeBytes = 120402L,
                                                isFavorite = true
                                            )
                                            viewModel.updateDocument(reportDoc)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.AddCircle, contentDescription = "Mock Data")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generate Mock Scanner Documents")
                                }

                                Button(
                                    onClick = { showResetConfirmation = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = "Reset")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Reset & Flush Database")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for Database Row Inspector
    selectedDocForInspector?.let { doc ->
        AlertDialog(
            onDismissRequest = { selectedDocForInspector = null },
            title = { Text("Inspect Row: ${doc.name}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Document Properties (Room Entity)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    SelectionContainer {
                        Column {
                            Text("ID: ${doc.id}", style = MaterialTheme.typography.bodySmall)
                            Text("Name: ${doc.name}", style = MaterialTheme.typography.bodyMedium)
                            Text("Folder ID: ${doc.folderId ?: "None"}", style = MaterialTheme.typography.bodyMedium)
                            Text("Created: ${SimpleDateFormat("dd-MM-yyyy hh:mm:ss a", Locale.getDefault()).format(Date(doc.dateCreated))}", style = MaterialTheme.typography.bodyMedium)
                            Text("Size: ${doc.sizeBytes} Bytes", style = MaterialTheme.typography.bodyMedium)
                            Text("Is Favorite: ${doc.isFavorite}", style = MaterialTheme.typography.bodyMedium)
                            Text("Is In Trash: ${doc.isTrash}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Image Paths (Comma separated)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(doc.imagePaths.ifEmpty { "No local images linked (Mock entry)" }, style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Extracted OCR Text Content", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = doc.ocrText ?: "No OCR text extracted yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            viewModel.deletePermanently(doc)
                            selectedDocForInspector = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Raw")
                    }
                    Button(onClick = { selectedDocForInspector = null }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    // Confirmation Dialog for Flush DB
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Flush Scanner Database?") },
            text = { Text("Are you absolutely sure you want to completely clear all documents and files? This action is irreversible and cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        allDocs.forEach { doc ->
                            viewModel.deletePermanently(doc)
                        }
                        showResetConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Flush All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Simple SelectionContainer wrapper for Copy/Paste raw text
@Composable
fun SelectionContainer(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        content()
    }
}
