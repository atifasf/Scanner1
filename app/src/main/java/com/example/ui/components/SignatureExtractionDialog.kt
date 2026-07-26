package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.ai.AISignatureExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DragHandle { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, BOTTOM, LEFT, RIGHT, CENTER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureExtractionDialog(
    documentBitmap: Bitmap,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var signatureRect by remember { mutableStateOf<Rect?>(null) }
    var transparentSignatureBmp by remember { mutableStateOf<Bitmap?>(null) }
    var isDetecting by remember { mutableStateOf(true) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var customSigName by remember { mutableStateOf("") }

    LaunchedEffect(documentBitmap) {
        withContext(Dispatchers.IO) {
            val detectedBox = AISignatureExtractor.detectSignatureRegion(documentBitmap)
            val extracted = AISignatureExtractor.extractTransparentSignature(documentBitmap, detectedBox)
            signatureRect = detectedBox
            transparentSignatureBmp = extracted
            isDetecting = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Gesture, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("AI Signature Extractor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .onGloballyPositioned { containerSize = it.size }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                val maxX = (containerSize.width * (zoomScale - 1)) / 2f
                                val maxY = (containerSize.height * (zoomScale - 1)) / 2f
                                panOffset = Offset(
                                    x = (panOffset.x + pan.x).coerceIn(-maxX, maxX),
                                    y = (panOffset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val box = signatureRect
                    val scaleX = if (containerSize.width > 0) containerSize.width.toFloat() / documentBitmap.width else 1.0f
                    val scaleY = if (containerSize.height > 0) containerSize.height.toFloat() / documentBitmap.height else 1.0f
                    val imgScale = minOf(scaleX, scaleY)
                    val offsetX = (containerSize.width - documentBitmap.width * imgScale) / 2f
                    val offsetY = (containerSize.height - documentBitmap.height * imgScale) / 2f

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = panOffset.x,
                                translationY = panOffset.y
                            )
                    ) {
                        Image(
                            bitmap = documentBitmap.asImageBitmap(),
                            contentDescription = "Document Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        if (box != null && containerSize.width > 0) {
                            var dragHandle by remember { mutableStateOf(DragHandle.NONE) }
                            var dragFractionX by remember { mutableFloatStateOf(0f) }
                            var dragFractionY by remember { mutableFloatStateOf(0f) }
                            
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(containerSize) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val currentBox = signatureRect ?: return@detectDragGestures
                                                val touchX = (offset.x - offsetX) / imgScale
                                                val touchY = (offset.y - offsetY) / imgScale
                                                val handleRadius = 80f / zoomScale // easier to grab when zoomed in
                                                
                                                dragFractionX = 0f
                                                dragFractionY = 0f
                                                
                                                dragHandle = when {
                                                    touchX in (currentBox.left - handleRadius)..(currentBox.left + handleRadius) && touchY in (currentBox.top - handleRadius)..(currentBox.top + handleRadius) -> DragHandle.TOP_LEFT
                                                    touchX in (currentBox.right - handleRadius)..(currentBox.right + handleRadius) && touchY in (currentBox.top - handleRadius)..(currentBox.top + handleRadius) -> DragHandle.TOP_RIGHT
                                                    touchX in (currentBox.left - handleRadius)..(currentBox.left + handleRadius) && touchY in (currentBox.bottom - handleRadius)..(currentBox.bottom + handleRadius) -> DragHandle.BOTTOM_LEFT
                                                    touchX in (currentBox.right - handleRadius)..(currentBox.right + handleRadius) && touchY in (currentBox.bottom - handleRadius)..(currentBox.bottom + handleRadius) -> DragHandle.BOTTOM_RIGHT
                                                    touchX in (currentBox.left - handleRadius)..(currentBox.left + handleRadius) && touchY in currentBox.top.toFloat()..currentBox.bottom.toFloat() -> DragHandle.LEFT
                                                    touchX in (currentBox.right - handleRadius)..(currentBox.right + handleRadius) && touchY in currentBox.top.toFloat()..currentBox.bottom.toFloat() -> DragHandle.RIGHT
                                                    touchY in (currentBox.top - handleRadius)..(currentBox.top + handleRadius) && touchX in currentBox.left.toFloat()..currentBox.right.toFloat() -> DragHandle.TOP
                                                    touchY in (currentBox.bottom - handleRadius)..(currentBox.bottom + handleRadius) && touchX in currentBox.left.toFloat()..currentBox.right.toFloat() -> DragHandle.BOTTOM
                                                    touchX in currentBox.left.toFloat()..currentBox.right.toFloat() && touchY in currentBox.top.toFloat()..currentBox.bottom.toFloat() -> DragHandle.CENTER
                                                    else -> DragHandle.NONE
                                                }
                                            },
                                            onDragEnd = {
                                                dragHandle = DragHandle.NONE
                                                val currentBox = signatureRect
                                                if (currentBox != null) {
                                                    scope.launch(Dispatchers.IO) {
                                                        transparentSignatureBmp = AISignatureExtractor.extractTransparentSignature(documentBitmap, currentBox)
                                                    }
                                                }
                                            },
                                            onDragCancel = {
                                                dragHandle = DragHandle.NONE
                                            },
                                            onDrag = { change, dragAmount ->
                                                if (dragHandle == DragHandle.NONE) return@detectDragGestures
                                                change.consume()
                                                
                                                val currentBox = signatureRect ?: return@detectDragGestures
                                                
                                                val rawDx = dragAmount.x / imgScale + dragFractionX
                                                val rawDy = dragAmount.y / imgScale + dragFractionY
                                                
                                                val dxImg = rawDx.toInt()
                                                val dyImg = rawDy.toInt()
                                                
                                                dragFractionX = rawDx - dxImg
                                                dragFractionY = rawDy - dyImg
                                                
                                                val minSize = 20
                                                var newLeft = currentBox.left
                                                var newTop = currentBox.top
                                                var newRight = currentBox.right
                                                var newBottom = currentBox.bottom
                                                
                                                when (dragHandle) {
                                                    DragHandle.CENTER -> {
                                                        newLeft = (currentBox.left + dxImg).coerceIn(0, documentBitmap.width - currentBox.width())
                                                        newTop = (currentBox.top + dyImg).coerceIn(0, documentBitmap.height - currentBox.height())
                                                        newRight = newLeft + currentBox.width()
                                                        newBottom = newTop + currentBox.height()
                                                    }
                                                    DragHandle.TOP_LEFT -> {
                                                        newLeft = (currentBox.left + dxImg).coerceIn(0, currentBox.right - minSize)
                                                        newTop = (currentBox.top + dyImg).coerceIn(0, currentBox.bottom - minSize)
                                                    }
                                                    DragHandle.TOP_RIGHT -> {
                                                        newRight = (currentBox.right + dxImg).coerceIn(currentBox.left + minSize, documentBitmap.width)
                                                        newTop = (currentBox.top + dyImg).coerceIn(0, currentBox.bottom - minSize)
                                                    }
                                                    DragHandle.BOTTOM_LEFT -> {
                                                        newLeft = (currentBox.left + dxImg).coerceIn(0, currentBox.right - minSize)
                                                        newBottom = (currentBox.bottom + dyImg).coerceIn(currentBox.top + minSize, documentBitmap.height)
                                                    }
                                                    DragHandle.BOTTOM_RIGHT -> {
                                                        newRight = (currentBox.right + dxImg).coerceIn(currentBox.left + minSize, documentBitmap.width)
                                                        newBottom = (currentBox.bottom + dyImg).coerceIn(currentBox.top + minSize, documentBitmap.height)
                                                    }
                                                    DragHandle.TOP -> {
                                                        newTop = (currentBox.top + dyImg).coerceIn(0, currentBox.bottom - minSize)
                                                    }
                                                    DragHandle.BOTTOM -> {
                                                        newBottom = (currentBox.bottom + dyImg).coerceIn(currentBox.top + minSize, documentBitmap.height)
                                                    }
                                                    DragHandle.LEFT -> {
                                                        newLeft = (currentBox.left + dxImg).coerceIn(0, currentBox.right - minSize)
                                                    }
                                                    DragHandle.RIGHT -> {
                                                        newRight = (currentBox.right + dxImg).coerceIn(currentBox.left + minSize, documentBitmap.width)
                                                    }
                                                    DragHandle.NONE -> {}
                                                }
                                                signatureRect = Rect(newLeft, newTop, newRight, newBottom)
                                            }
                                        )
                                    }
                            ) {
                                val leftPx = box.left * imgScale + offsetX
                                val topPx = box.top * imgScale + offsetY
                                val rightPx = box.right * imgScale + offsetX
                                val bottomPx = box.bottom * imgScale + offsetY
                                val widthPx = rightPx - leftPx
                                val heightPx = bottomPx - topPx

                                drawRect(
                                    color = Color(0x3300E676),
                                    topLeft = Offset(leftPx, topPx),
                                    size = Size(widthPx, heightPx)
                                )
                                drawRect(
                                    color = Color(0xFF00E676),
                                    topLeft = Offset(leftPx, topPx),
                                    size = Size(widthPx, heightPx),
                                    style = Stroke(width = (2.dp.toPx() / zoomScale))
                                )
                                
                                val handleRadius = 12.dp.toPx() / zoomScale
                                val corners = listOf(
                                    Offset(leftPx, topPx),
                                    Offset(rightPx, topPx),
                                    Offset(leftPx, bottomPx),
                                    Offset(rightPx, bottomPx)
                                )
                                corners.forEach {
                                    drawCircle(
                                        color = Color.White,
                                        radius = handleRadius,
                                        center = it
                                    )
                                    drawCircle(
                                        color = Color(0xFF00E676),
                                        radius = handleRadius,
                                        center = it,
                                        style = Stroke(width = 2.dp.toPx() / zoomScale)
                                    )
                                }
                            }
                        }
                    }

                    if (isDetecting) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val sigBmp = transparentSignatureBmp
                if (sigBmp != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Extracted Transparent PNG Signature", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = sigBmp.asImageBitmap(),
                                    contentDescription = "Signature Preview",
                                    modifier = Modifier.fillMaxHeight().padding(8.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customSigName,
                                onValueChange = { customSigName = it },
                                label = { Text("Signature Name (Optional)") },
                                placeholder = { Text("e.g. My Primary Signature") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val bmp = transparentSignatureBmp
                            if (bmp != null) {
                                scope.launch(Dispatchers.IO) {
                                    val nameToUse = customSigName.ifBlank { "Signature_${System.currentTimeMillis() % 10000}" }
                                    
                                    // 1. Save to Signature Library ("My Signatures")
                                    com.example.ui.ai.SignatureLibraryManager.saveSignature(context, bmp, nameToUse)
                                    
                                    // 2. Save PNG directly to Gallery Pictures/Signatures
                                    val savedToGallery = AISignatureExtractor.saveSignatureToGallery(context, bmp, nameToUse)
                                    
                                    withContext(Dispatchers.Main) {
                                        if (savedToGallery) {
                                            Toast.makeText(context, "Saved '$nameToUse' to My Signatures & Phone Gallery!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Saved '$nameToUse' to My Signatures!", Toast.LENGTH_SHORT).show()
                                        }
                                        onDismiss()
                                    }
                                }
                            }
                        },
                        enabled = transparentSignatureBmp != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save PNG", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val bmp = transparentSignatureBmp
                            if (bmp != null) {
                                scope.launch(Dispatchers.IO) {
                                    val file = AISignatureExtractor.saveSignaturePng(context, bmp, "TempSignature")
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newUri(context.contentResolver, "Signature", uri)
                                    clipboard.setPrimaryClip(clip)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Signature copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = transparentSignatureBmp != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
