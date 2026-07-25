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

    // Detect signature region on dialog open
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
                .fillMaxSize()
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

                // Document Interactive Canvas with Signature Box Selection
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .onGloballyPositioned { containerSize = it.size },
                    contentAlignment = Alignment.Center
                ) {
                    val box = signatureRect
                    val scaleX = if (containerSize.width > 0) containerSize.width.toFloat() / documentBitmap.width else 1.0f
                    val scaleY = if (containerSize.height > 0) containerSize.height.toFloat() / documentBitmap.height else 1.0f
                    val scale = minOf(scaleX, scaleY)

                    val offsetX = (containerSize.width - documentBitmap.width * scale) / 2f
                    val offsetY = (containerSize.height - documentBitmap.height * scale) / 2f

                    Image(
                        bitmap = documentBitmap.asImageBitmap(),
                        contentDescription = "Document Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    if (box != null && containerSize.width > 0) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(containerSize, box) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val dxImg = (dragAmount.x / scale).toInt()
                                        val dyImg = (dragAmount.y / scale).toInt()

                                        val newRect = Rect(
                                            (box.left + dxImg).coerceIn(0, documentBitmap.width - 50),
                                            (box.top + dyImg).coerceIn(0, documentBitmap.height - 50),
                                            (box.right + dxImg).coerceIn(50, documentBitmap.width),
                                            (box.bottom + dyImg).coerceIn(50, documentBitmap.height)
                                        )
                                        signatureRect = newRect
                                        scope.launch(Dispatchers.IO) {
                                            transparentSignatureBmp = AISignatureExtractor.extractTransparentSignature(documentBitmap, newRect)
                                        }
                                    }
                                }
                        ) {
                            val leftPx = box.left * scale + offsetX
                            val topPx = box.top * scale + offsetY
                            val rightPx = box.right * scale + offsetX
                            val bottomPx = box.bottom * scale + offsetY

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
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }

                    if (isDetecting) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Transparent Signature Preview Box
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
                                    .height(90.dp)
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
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val bmp = transparentSignatureBmp
                            if (bmp != null) {
                                scope.launch(Dispatchers.IO) {
                                    val file = AISignatureExtractor.saveSignaturePng(context, bmp)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Signature saved to ${file.name}", Toast.LENGTH_SHORT).show()
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
