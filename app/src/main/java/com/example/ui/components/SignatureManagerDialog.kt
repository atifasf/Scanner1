package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.ai.AISignatureExtractor
import com.example.ui.ai.SavedSignature
import com.example.ui.ai.SignatureLibraryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureManagerDialog(
    documentFile: File,
    onDismiss: () -> Unit,
    onDocumentUpdated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableIntStateOf(0) }
    var documentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Extracted transparent signature state
    var extractedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var customSigName by remember { mutableStateOf("") }
    var showLibraryDialog by remember { mutableStateOf(false) }
    var selectedSignatureForPaste by remember { mutableStateOf<SavedSignature?>(null) }

    // Load document bitmap
    LaunchedEffect(documentFile) {
        withContext(Dispatchers.IO) {
            try {
                val bmp = BitmapFactory.decodeFile(documentFile.absolutePath)
                documentBitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (selectedSignatureForPaste != null) {
        SignaturePasteOverlayEditor(
            documentFile = documentFile,
            selectedSignature = selectedSignatureForPaste!!,
            onDismiss = { selectedSignatureForPaste = null },
            onSaveSuccess = {
                selectedSignatureForPaste = null
                onDocumentUpdated()
                onDismiss()
            }
        )
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header & Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Gesture,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "AI Signature Manager",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row {
                        IconButton(onClick = { showLibraryDialog = true }) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = "My Signatures")
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Tabs
                TabRow(selectedTabIndex = activeTab) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Save Signature")
                            }
                        }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Paste Signature")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (activeTab == 0) {
                    // TAB 0: Save Signature (Manual Crop)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Position the box around your signature. You can drag edges, zoom, and pan for a precise crop.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (extractedBitmap == null) {
                            // Show Crop Editor
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (documentBitmap != null) {
                                    SignatureCropEditor(
                                        documentBitmap = documentBitmap!!,
                                        onExtract = { cropRect ->
                                            scope.launch {
                                                extractedBitmap = AISignatureExtractor.extractTransparentSignature(documentBitmap!!, cropRect)
                                            }
                                        }
                                    )
                                } else {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                }
                            }
                        } else {
                            // Show Extracted Preview
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Extracted Transparent PNG",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White)
                                            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = extractedBitmap!!.asImageBitmap(),
                                            contentDescription = "Signature Preview",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = customSigName,
                                onValueChange = { customSigName = it },
                                label = { Text("Signature Name") },
                                placeholder = { Text("e.g. My Primary Signature") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { extractedBitmap = null },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Adjust Crop")
                                }
                                Button(
                                    onClick = {
                                        val sigName = customSigName.ifBlank { "Signature_${System.currentTimeMillis() % 10000}" }
                                        val saved = SignatureLibraryManager.saveSignature(
                                            context = context,
                                            bitmap = extractedBitmap!!,
                                            name = sigName
                                        )
                                        Toast.makeText(context, "Saved '${saved.name}' to My Signatures!", Toast.LENGTH_SHORT).show()
                                        extractedBitmap = null // Reset
                                        activeTab = 1 // Switch to Paste Signature tab
                                    },
                                    modifier = Modifier.weight(1.5f)
                                ) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save Signature")
                                }
                            }
                        }
                    }
                } else {
                    // TAB 1: Paste Signature
                    val savedSignatures = remember { SignatureLibraryManager.getSavedSignatures(context) }
                    
                    if (savedSignatures.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FolderSpecial,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("No saved signatures found.")
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(onClick = { activeTab = 0 }) {
                                    Text("Extract & Save Signature First")
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Select a signature from your library to place on this document:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(savedSignatures, key = { it.id }) { sig ->
                                    val file = File(sig.imagePath)
                                    val bmp = remember(sig.imagePath) {
                                        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                                    }

                                    Card(
                                        onClick = { selectedSignatureForPaste = sig },
                                        modifier = Modifier
                                            .width(160.dp)
                                            .height(200.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(10.dp)
                                                .fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (bmp != null) {
                                                    Image(
                                                        bitmap = bmp.asImageBitmap(),
                                                        contentDescription = sig.name,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(6.dp),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = sig.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Button(
                                                onClick = { selectedSignatureForPaste = sig },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Select & Overlay", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLibraryDialog) {
        SignatureLibraryDialog(
            onDismiss = { showLibraryDialog = false },
            onSelectSignatureForPaste = { sig ->
                showLibraryDialog = false
                selectedSignatureForPaste = sig
            }
        )
    }
}
