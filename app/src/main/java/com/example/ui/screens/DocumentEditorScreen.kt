package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.DocumentEntity
import com.example.ui.AutoDeskewEnhancer
import com.example.ui.DocumentViewModel
import com.example.ui.EnhancementMode
import com.example.ui.ExportHelper
import com.example.ui.ImageEnhancer
import com.example.ui.OCRHelper
import com.example.ui.ai.AIMagicScan
import com.example.ui.components.EraserCanvasEditor
import com.example.ui.components.SignatureCropEditor
import com.example.ui.components.SignatureManagerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditorScreen(
    documentId: String,
    viewModel: DocumentViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var document by remember { mutableStateOf<DocumentEntity?>(null) }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var imageRefreshTrigger by remember { mutableIntStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }

    // Dialog & overlay states
    var isCropActive by remember { mutableStateOf(false) }
    var editingFileForEraser by remember { mutableStateOf<File?>(null) }
    var signatureManagerFile by remember { mutableStateOf<File?>(null) }
    var activeFilterSheet by remember { mutableStateOf(false) }

    // OCR states
    var isOcrLoading by remember { mutableStateOf(false) }
    var isTableScanLoading by remember { mutableStateOf(false) }
    var extractedOcrText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(documentId) {
        document = viewModel.getDocumentById(documentId)
        extractedOcrText = document?.ocrText
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Edit - ${document?.name ?: "Document"}",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        document?.let { doc ->
                            val pages = doc.imagePaths.split(",").filter { it.isNotBlank() }
                            Text(
                                text = "Page ${selectedPageIndex + 1} of ${pages.size}",
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
                    Button(
                        onClick = {
                            document?.let { doc ->
                                viewModel.rebuildPdfForDocument(doc) {
                                    Toast.makeText(context, "Document saved!", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                }
                            } ?: onNavigateBack()
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
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
                    Text("No pages available")
                }
            } else {
                val currentPagePath = pagePaths.getOrNull(selectedPageIndex) ?: pagePaths.first()
                val currentPageFile = remember(currentPagePath) { File(currentPagePath) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // --- Page Navigation Ribbon (Thumbnails / Switcher) ---
                    if (pagePaths.size > 1) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(pagePaths) { index, path ->
                                    val isSelected = index == selectedPageIndex
                                    Box(
                                        modifier = Modifier
                                            .size(width = 54.dp, height = 72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedPageIndex = index }
                                    ) {
                                        AsyncImage(
                                            model = remember(path, imageRefreshTrigger) {
                                                ImageRequest.Builder(context)
                                                    .data(File(path))
                                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                                    .build()
                                            },
                                            contentDescription = "Page ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .background(Color.Black.copy(alpha = 0.65f))
                                                .padding(vertical = 2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- Current Page Preview Canvas ---
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF121212))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        } else {
                            AsyncImage(
                                model = remember(currentPagePath, imageRefreshTrigger) {
                                    ImageRequest.Builder(context)
                                        .data(File(currentPagePath))
                                        .memoryCachePolicy(CachePolicy.DISABLED)
                                        .build()
                                },
                                contentDescription = "Page ${selectedPageIndex + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // --- Main Editing Tools Action Toolbar ---
                    Surface(
                        tonalElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            // Primary Toolbar Buttons
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 1. Rotate Left
                                item {
                                    EditorToolButton(
                                        icon = Icons.Default.RotateLeft,
                                        label = "Rotate L",
                                        onClick = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                isProcessing = true
                                                rotateImage(currentPageFile, -90f)
                                                withContext(Dispatchers.Main) {
                                                    imageRefreshTrigger++
                                                    isProcessing = false
                                                }
                                            }
                                        }
                                    )
                                }

                                // 2. Rotate Right
                                item {
                                    EditorToolButton(
                                        icon = Icons.Default.RotateRight,
                                        label = "Rotate R",
                                        onClick = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                isProcessing = true
                                                rotateImage(currentPageFile, 90f)
                                                withContext(Dispatchers.Main) {
                                                    imageRefreshTrigger++
                                                    isProcessing = false
                                                }
                                            }
                                        }
                                    )
                                }

                                // 3. Crop
                                item {
                                    EditorToolButton(
                                        icon = Icons.Default.Crop,
                                        label = "Crop",
                                        onClick = { isCropActive = true }
                                    )
                                }

                                // 4. Filters / Color Modes
                                item {
                                    EditorToolButton(
                                        icon = Icons.Default.ColorLens,
                                        label = "Filters",
                                        onClick = { activeFilterSheet = true }
                                    )
                                }

                                // 5. Auto Deskew / Straighten
                                item {
                                    EditorToolButton(
                                        icon = Icons.Default.AutoFixHigh,
                                        label = "Deskew",
                                        onClick = {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                isProcessing = true
                                                val bmp = BitmapFactory.decodeFile(currentPagePath)
                                                if (bmp != null) {
                                                    val angle = AutoDeskewEnhancer.detectTextSkewAngle(bmp)
                                                    if (kotlin.math.abs(angle) > 0.5f) {
                                                        val straightened = AutoDeskewEnhancer.rotateBitmap(bmp, -angle)
                                                        currentPageFile.outputStream().use { out ->
                                                            straightened.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                                        }
                                                    }
                                                }
                                                withContext(Dispatchers.Main) {
                                                    imageRefreshTrigger++
                                                    isProcessing = false
                                                    Toast.makeText(context, "Auto Deskew applied", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    )
                                }

                                // 6. Erase & Clean
                                item {
                                    EditorToolButton(
                                        icon = Icons.Default.Gesture,
                                        label = "Erase",
                                        onClick = { editingFileForEraser = currentPageFile }
                                    )
                                }

                                // 7. Signature
                                item {
                                    EditorToolButton(
                                        icon = Icons.Default.Draw,
                                        label = "Signature",
                                        onClick = { signatureManagerFile = currentPageFile }
                                    )
                                }

                                // 8. OCR Extract Text
                                item {
                                    EditorToolButton(
                                        icon = Icons.Default.TextFields,
                                        label = "OCR Text",
                                        onClick = {
                                            isOcrLoading = true
                                            val uri = android.net.Uri.fromFile(currentPageFile)
                                            val lang = sharedPrefs.getString("ocr_language", "en") ?: "en"
                                            OCRHelper.extractText(context, uri, languageCode = lang,
                                                onSuccess = { text ->
                                                    isOcrLoading = false
                                                    extractedOcrText = text
                                                    document?.let { d ->
                                                        val updated = d.copy(ocrText = text)
                                                        viewModel.updateDocument(updated)
                                                        document = updated
                                                    }
                                                    Toast.makeText(context, "Text extracted successfully!", Toast.LENGTH_SHORT).show()
                                                },
                                                onError = {
                                                    isOcrLoading = false
                                                    Toast.makeText(context, "OCR failed", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    )
                                }

                                // 9. Scan Table
                                item {
                                    EditorToolButton(
                                        icon = Icons.Default.GridOn,
                                        label = "Scan Table",
                                        onClick = {
                                            isTableScanLoading = true
                                            val uri = android.net.Uri.fromFile(currentPageFile)
                                            OCRHelper.extractTableAsCsv(context, uri,
                                                onSuccess = { csvText ->
                                                    isTableScanLoading = false
                                                    extractedOcrText = csvText
                                                    document?.let { d ->
                                                        val updated = d.copy(ocrText = csvText)
                                                        viewModel.updateDocument(updated)
                                                        document = updated
                                                    }
                                                    Toast.makeText(context, "Table extracted!", Toast.LENGTH_SHORT).show()
                                                },
                                                onError = {
                                                    isTableScanLoading = false
                                                    Toast.makeText(context, "Table scan failed", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Extracted OCR / Table Text Card if available
                    extractedOcrText?.let { text ->
                        if (text.isNotBlank()) {
                            Surface(
                                tonalElevation = 4.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Extracted Text", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        Spacer(modifier = Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Extracted Text", text))
                                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = { extractedOcrText = null },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = text,
                                        maxLines = 4,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState())
                                    )
                                }
                            }
                        }
                    }
                }

                // --- Filter Selection Bottom Sheet Dialog ---
                if (activeFilterSheet) {
                    AlertDialog(
                        onDismissRequest = { activeFilterSheet = false },
                        title = { Text("Select Document Filter") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterOptionItem(label = "Magic Color (Enhanced)", icon = Icons.Default.AutoFixHigh) {
                                    applyPresetFilter(context, currentPageFile, EnhancementMode.MAGIC_COLOR) {
                                        imageRefreshTrigger++
                                        activeFilterSheet = false
                                    }
                                }
                                FilterOptionItem(label = "B&W High Contrast", icon = Icons.Default.Contrast) {
                                    applyPresetFilter(context, currentPageFile, EnhancementMode.BLACK_AND_WHITE) {
                                        imageRefreshTrigger++
                                        activeFilterSheet = false
                                    }
                                }
                                FilterOptionItem(label = "Grayscale", icon = Icons.Default.FilterBAndW) {
                                    applyPresetFilter(context, currentPageFile, EnhancementMode.GRAYSCALE) {
                                        imageRefreshTrigger++
                                        activeFilterSheet = false
                                    }
                                }
                                FilterOptionItem(label = "Original Color", icon = Icons.Default.Palette) {
                                    applyPresetFilter(context, currentPageFile, EnhancementMode.ORIGINAL) {
                                        imageRefreshTrigger++
                                        activeFilterSheet = false
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { activeFilterSheet = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // --- Crop Dialog Overlay ---
                if (isCropActive) {
                    val bmp = remember(currentPagePath, imageRefreshTrigger) {
                        BitmapFactory.decodeFile(currentPagePath)
                    }
                    if (bmp != null) {
                        SignatureCropEditor(
                            documentBitmap = bmp,
                            onExtract = { rect ->
                                isCropActive = false
                                coroutineScope.launch(Dispatchers.IO) {
                                    val safeLeft = rect.left.coerceIn(0, bmp.width - 1)
                                    val safeTop = rect.top.coerceIn(0, bmp.height - 1)
                                    val safeRight = rect.right.coerceIn(safeLeft + 1, bmp.width)
                                    val safeBottom = rect.bottom.coerceIn(safeTop + 1, bmp.height)
                                    val croppedBmp = Bitmap.createBitmap(bmp, safeLeft, safeTop, safeRight - safeLeft, safeBottom - safeTop)
                                    currentPageFile.outputStream().use { out ->
                                        croppedBmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                    }
                                    withContext(Dispatchers.Main) {
                                        imageRefreshTrigger++
                                        Toast.makeText(context, "Page cropped", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    } else {
                        isCropActive = false
                    }
                }

                // --- Eraser Canvas Overlay ---
                if (editingFileForEraser != null) {
                    EraserCanvasEditor(
                        imageFile = editingFileForEraser!!,
                        onSave = {
                            imageRefreshTrigger++
                            editingFileForEraser = null
                            Toast.makeText(context, "Erase & Clean saved", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { editingFileForEraser = null }
                    )
                }

                // --- Signature Manager Dialog Overlay ---
                if (signatureManagerFile != null) {
                    SignatureManagerDialog(
                        documentFile = signatureManagerFile!!,
                        onDismiss = { signatureManagerFile = null },
                        onDocumentUpdated = {
                            imageRefreshTrigger++
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FilterOptionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

private fun rotateImage(file: File, angleDegrees: Float) {
    try {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
        val matrix = Matrix().apply { postRotate(angleDegrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        file.outputStream().use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun applyPresetFilter(context: Context, file: File, mode: EnhancementMode, onComplete: () -> Unit) {
    try {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
        val processed = AutoDeskewEnhancer.enhanceDocument(bitmap, mode)
        file.outputStream().use { out ->
            processed.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        onComplete()
    } catch (e: Exception) {
        e.printStackTrace()
        onComplete()
    }
}
