package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.BackupHelper
import com.example.ui.DocumentViewModel
import com.example.ui.components.AutoDeskewFineTuneDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsTab(viewModel: DocumentViewModel, padding: PaddingValues, context: Context) {
    val coroutineScope = rememberCoroutineScope()
    var selectedToolFile by remember { mutableStateOf<File?>(null) }
    var isProcessingToolImage by remember { mutableStateOf(false) }
    var showSignatureLibrary by remember { mutableStateOf(false) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val success = BackupHelper.createBackupZip(context, uri)
                val msg = if (success) "Backup created successfully!" else "Failed to create backup."
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                isProcessingToolImage = true
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val tempFile = File(context.cacheDir, "temp_tool_${UUID.randomUUID()}.jpg")
                    tempFile.outputStream().use { output ->
                        inputStream?.copyTo(output)
                    }
                    inputStream?.close()
                    withContext(Dispatchers.Main) {
                        selectedToolFile = tempFile
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isProcessingToolImage = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Scanner Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // AI Auto Deskew & Perspective Correction Tool
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "AI Auto Deskew & Perspective Correction",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "CamScanner-style auto document boundary detection, perspective warping, text line horizontal alignment, shadow reduction, and background whitening.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    enabled = !isProcessingToolImage
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Document Image to Deskew")
                }
            }
        }

        // AI Signature Extractor Card
        var selectedSigToolFile by remember { mutableStateOf<File?>(null) }
        val sigImagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val tempFile = File(context.cacheDir, "temp_sig_${UUID.randomUUID()}.jpg")
                        tempFile.outputStream().use { output ->
                            inputStream?.copyTo(output)
                        }
                        inputStream?.close()
                        withContext(Dispatchers.Main) {
                            selectedSigToolFile = tempFile
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Gesture,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "AI Signature Detector & Extractor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Detect handwritten signatures on scanned documents and save/copy as transparent PNG.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { sigImagePickerLauncher.launch("image/*") }
                    ) {
                        Icon(Icons.Default.Gesture, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Extract Signature")
                    }

                    OutlinedButton(
                        onClick = { showSignatureLibrary = true }
                    ) {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("My Signatures")
                    }
                }
            }
        }

        if (selectedSigToolFile != null) {
            val bmp = remember(selectedSigToolFile) { BitmapFactory.decodeFile(selectedSigToolFile!!.absolutePath) }
            if (bmp != null) {
                com.example.ui.components.SignatureExtractionDialog(
                    documentBitmap = bmp,
                    onDismiss = { selectedSigToolFile = null }
                )
            }
        }

        // Backup Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Backup & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = {
                        createBackupLauncher.launch("DocumentScannerBackup_${System.currentTimeMillis()}.zip")
                    }) {
                        Icon(Icons.Default.Archive, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Backup ZIP")
                    }
                }
            }
        }
    }

    if (showSignatureLibrary) {
        com.example.ui.components.SignatureLibraryDialog(
            onDismiss = { showSignatureLibrary = false }
        )
    }

    // Auto Deskew & Fine-Tune Dialog
    if (selectedToolFile != null) {
        AutoDeskewFineTuneDialog(
            imageFile = selectedToolFile!!,
            onDismiss = { selectedToolFile = null },
            onApply = { newBitmap ->
                coroutineScope.launch(Dispatchers.IO) {
                    val uriList = listOf(Uri.fromFile(selectedToolFile))
                    viewModel.saveScannedDocumentWithFormat(
                        imageUris = uriList,
                        format = DocumentViewModel.OutputFormat.PDF,
                        isSearchablePdf = false,
                        customName = "Deskewed_Doc_${System.currentTimeMillis() % 10000}",
                        onComplete = {
                            selectedToolFile = null
                            android.widget.Toast.makeText(context, "Deskewed document saved!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        )
    }
}
