package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.DocumentEntity
import com.example.ui.DocumentViewModel
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.ScannerHelper
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    documentId: String,
    viewModel: DocumentViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit
) {
    val context = LocalContext.current
    var document by remember { mutableStateOf<DocumentEntity?>(null) }
    var imageRefreshTrigger by remember { mutableIntStateOf(0) }
    var showAddOptionsDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
                title = {
                    Column {
                        Text(
                            text = document?.name ?: "PDF Viewer",
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium
                        )
                        document?.let { doc ->
                            val pages = doc.imagePaths.split(",").filter { it.isNotBlank() }
                            Text(
                                text = "${pages.size} page${if (pages.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        document?.let { doc ->
                            viewModel.toggleFavorite(doc)
                            document = doc.copy(isFavorite = !doc.isFavorite)
                        }
                    }) {
                        Icon(
                            imageVector = if (document?.isFavorite == true) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (document?.isFavorite == true) Color(0xFFFFD700) else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = {
                        document?.let { doc ->
                            val file = doc.pdfPath?.let { path -> File(path) }
                                ?: doc.imagePaths.split(",").firstOrNull { it.isNotBlank() }?.let { path -> File(path) }
                            if (file != null && file.exists()) {
                                com.example.ui.ShareHelper.shareDocument(context, file, doc.name)
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = { onNavigateToEditor(documentId) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        val currentDoc = document
        if (currentDoc == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val pdfFile = remember(currentDoc.pdfPath) {
                currentDoc.pdfPath?.let { File(it) }
            }
            val isPdfEncrypted = remember(pdfFile) {
                pdfFile != null && pdfFile.exists() && com.example.ui.ai.AIPdfPasswordProtection.isEncrypted(pdfFile)
            }
            var isUnlocked by remember { mutableStateOf(!isPdfEncrypted) }
            var passwordInput by remember { mutableStateOf("") }
            var passwordVisible by remember { mutableStateOf(false) }
            var passwordError by remember { mutableStateOf<String?>(null) }

            if (isPdfEncrypted && !isUnlocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = "Protected Document",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "This document is password protected. Enter the password to unlock '${currentDoc.name}'.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = {
                                    passwordInput = it
                                    passwordError = null
                                },
                                label = { Text("Password") },
                                placeholder = { Text("Enter password") },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                trailingIcon = {
                                    val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(icon, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                                    }
                                },
                                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                isError = passwordError != null,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            if (passwordError != null) {
                                Text(
                                    text = passwordError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Button(
                                onClick = {
                                    if (passwordInput.isBlank()) {
                                        passwordError = "Password cannot be empty"
                                        return@Button
                                    }
                                    if (pdfFile != null) {
                                        val tempDecrypted = File(context.cacheDir, "unlocked_${currentDoc.id}.pdf")
                                        val success = com.example.ui.ai.AIPdfPasswordProtection.decryptPdf(pdfFile, tempDecrypted, passwordInput)
                                        if (success) {
                                            isUnlocked = true
                                            passwordError = null
                                        } else {
                                            passwordError = "Incorrect password. Please try again."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unlock Document", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                val pagePaths = remember(currentDoc.imagePaths) {
                    currentDoc.imagePaths.split(",").filter { it.isNotBlank() }
                }

            if (pagePaths.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No pages found in document", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                val listState = rememberLazyListState()
                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }

                val firstVisibleItem = remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color(0xFF1E1E1E))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (scale > 1.1f) {
                                            scale = 1f
                                            offsetX = 0f
                                            offsetY = 0f
                                        } else {
                                            scale = 2.5f
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    scale = newScale
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(pagePaths) { index, path ->
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        AsyncImage(
                                            model = remember(path, imageRefreshTrigger) {
                                                ImageRequest.Builder(context)
                                                    .data(File(path))
                                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                                    .build()
                                            },
                                            contentDescription = "Page ${index + 1}",
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Page ${index + 1} of ${pagePaths.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                Button(
                                    onClick = { showAddOptionsDialog = true },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add Page")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add Page")
                                }
                            }
                        }
                    }

                    // Floating Page Indicator Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "Page ${firstVisibleItem.value} / ${pagePaths.size}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAddOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showAddOptionsDialog = false },
            title = { Text("Add Page", fontWeight = FontWeight.Bold) },
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
}
