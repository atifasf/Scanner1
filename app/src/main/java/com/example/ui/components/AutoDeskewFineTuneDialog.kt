package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import kotlin.math.roundToInt
import com.example.ui.AutoDeskewEnhancer
import com.example.ui.EnhancementMode
import com.example.ui.QuadPoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDeskewFineTuneDialog(
    imageFile: File,
    onDismiss: () -> Unit,
    onApply: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var quadPoints by remember { mutableStateOf<QuadPoints?>(null) }
    var userRotationAngle by remember { mutableFloatStateOf(0f) }
    var selectedMode by remember { mutableStateOf(EnhancementMode.MAGIC_COLOR) }

    var isProcessing by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Detecting document boundaries...") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandleIndex by remember { mutableStateOf(-1) }
    var showPreviewTab by remember { mutableStateOf(false) }

    // Load original bitmap and perform initial AI auto-detection
    LaunchedEffect(imageFile) {
        withContext(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val bmp = BitmapFactory.decodeFile(imageFile.absolutePath, opts)
                if (bmp != null) {
                    originalBitmap = bmp
                    val detectedQuad = AutoDeskewEnhancer.detectCorners(bmp)
                    quadPoints = detectedQuad

                    val autoDeskew = AutoDeskewEnhancer.detectTextSkewAngle(bmp)
                    userRotationAngle = autoDeskew

                    val processed = AutoDeskewEnhancer.autoProcessDocument(context, bmp, selectedMode)
                    previewBitmap = processed
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isProcessing = false
            }
        }
    }

    // Function to re-render preview
    fun updatePreview() {
        val bmp = originalBitmap ?: return
        val quad = quadPoints ?: return
        isProcessing = true
        statusText = "Warping & Enhancing page..."
        scope.launch(Dispatchers.IO) {
            try {
                var warped = AutoDeskewEnhancer.warpPerspective(bmp, quad)
                if (userRotationAngle != 0f) {
                    val rotated = AutoDeskewEnhancer.rotateBitmap(warped, userRotationAngle)
                    if (rotated != warped && warped != bmp) warped.recycle()
                    warped = rotated
                }
                val enhanced = AutoDeskewEnhancer.enhanceDocument(warped, selectedMode)
                if (warped != bmp && warped != enhanced) warped.recycle()
                previewBitmap = enhanced
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isProcessing = false
            }
        }
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
                // Top Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = "AI Auto Deskew",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "AI Deskew & Perspective",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mode Tabs: Crop Boundary vs Live Preview
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = !showPreviewTab,
                        onClick = { showPreviewTab = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("1. Fine-Tune Crop")
                        }
                    }
                    SegmentedButton(
                        selected = showPreviewTab,
                        onClick = {
                            updatePreview()
                            showPreviewTab = true
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("2. Page Preview")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Canvas / Interactive Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .onGloballyPositioned { containerSize = it.size },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = originalBitmap
                    val quad = quadPoints

                    if (bmp != null) {
                        if (showPreviewTab) {
                            val prevBmp = previewBitmap
                            if (prevBmp != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = prevBmp.asImageBitmap(),
                                    contentDescription = "Deskewed Page Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        } else if (containerSize.width > 0 && containerSize.height > 0 && quad != null) {
                            val imgW = bmp.width.toFloat()
                            val imgH = bmp.height.toFloat()

                            val scale = minOf(
                                containerSize.width.toFloat() / imgW,
                                containerSize.height.toFloat() / imgH
                            )
                            val offsetX = (containerSize.width - imgW * scale) / 2f
                            val offsetY = (containerSize.height - imgH * scale) / 2f

                            fun mapToScreen(pt: PointF): Offset {
                                return Offset(pt.x * scale + offsetX, pt.y * scale + offsetY)
                            }

                            fun mapToImage(offset: Offset): PointF {
                                val ix = ((offset.x - offsetX) / scale).coerceIn(0f, imgW)
                                val iy = ((offset.y - offsetY) / scale).coerceIn(0f, imgH)
                                return PointF(ix, iy)
                            }

                            val pTL = mapToScreen(quad.topLeft)
                            val pTR = mapToScreen(quad.topRight)
                            val pBR = mapToScreen(quad.bottomRight)
                            val pBL = mapToScreen(quad.bottomLeft)

                            // Render base image
                            androidx.compose.foundation.Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Original Scanned Document",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            // Interactive Canvas Overlay with draggable handles and quad polygon
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(containerSize, quad) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val handles = listOf(pTL, pTR, pBR, pBL)
                                                activeHandleIndex = handles.indexOfFirst {
                                                    hypot(it.x - offset.x, it.y - offset.y) < 60f
                                                }
                                            },
                                            onDrag = { change, _ ->
                                                if (activeHandleIndex >= 0) {
                                                    change.consume()
                                                    val newPt = mapToImage(change.position)
                                                    val newQuad = quad.copyPoints()
                                                    when (activeHandleIndex) {
                                                        0 -> newQuad.topLeft = newPt
                                                        1 -> newQuad.topRight = newPt
                                                        2 -> newQuad.bottomRight = newPt
                                                        3 -> newQuad.bottomLeft = newPt
                                                    }
                                                    quadPoints = newQuad
                                                }
                                            },
                                            onDragEnd = {
                                                activeHandleIndex = -1
                                                updatePreview()
                                            }
                                        )
                                    }
                            ) {
                                // Draw Quad Polygon Overlay
                                val path = Path().apply {
                                    moveTo(pTL.x, pTL.y)
                                    lineTo(pTR.x, pTR.y)
                                    lineTo(pBR.x, pBR.y)
                                    lineTo(pBL.x, pBL.y)
                                    close()
                                }

                                drawPath(
                                    path = path,
                                    color = Color(0x3300E676)
                                )
                                drawPath(
                                    path = path,
                                    color = Color(0xFF00E676),
                                    style = Stroke(width = 3.dp.toPx())
                                )

                                // Draw Handle Circles
                                val handles = listOf(pTL, pTR, pBR, pBL)
                                handles.forEachIndexed { idx, point ->
                                    val isDragging = idx == activeHandleIndex
                                    drawCircle(
                                        color = if (isDragging) Color(0xFFFFD54F) else Color.White,
                                        radius = if (isDragging) 18.dp.toPx() else 14.dp.toPx(),
                                        center = point
                                    )
                                    drawCircle(
                                        color = Color(0xFF00C853),
                                        radius = if (isDragging) 12.dp.toPx() else 8.dp.toPx(),
                                        center = point
                                    )
                                }
                            }
                        }
                    }

                    if (isProcessing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = Color.White)
                                Text(
                                    text = statusText,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Adjustment Controls
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Filter Mode Selection Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EnhancementMode.values().forEach { mode ->
                            val isSelected = selectedMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedMode = mode
                                    updatePreview()
                                },
                                label = {
                                    Text(
                                        text = when (mode) {
                                            EnhancementMode.MAGIC_COLOR -> "Magic Color"
                                            EnhancementMode.BLACK_AND_WHITE -> "B&W"
                                            EnhancementMode.GRAYSCALE -> "Grayscale"
                                            EnhancementMode.ORIGINAL -> "Original"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Skew & Rotation Control Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                userRotationAngle = (userRotationAngle - 90f) % 360f
                                updatePreview()
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Rotate Left")
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Fine Angle Deskew", style = MaterialTheme.typography.labelMedium)
                                Text("${userRotationAngle.roundToInt()}°", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = userRotationAngle,
                                onValueChange = { userRotationAngle = it },
                                onValueChangeFinished = { updatePreview() },
                                valueRange = -30f..30f,
                                steps = 60
                            )
                        }

                        IconButton(
                            onClick = {
                                userRotationAngle = (userRotationAngle + 90f) % 360f
                                updatePreview()
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate Right")
                        }

                        OutlinedButton(
                            onClick = {
                                isProcessing = true
                                statusText = "Recalculating AI boundary & text deskew..."
                                scope.launch(Dispatchers.IO) {
                                    val bmp = originalBitmap ?: return@launch
                                    val detected = AutoDeskewEnhancer.detectCorners(bmp)
                                    val skew = AutoDeskewEnhancer.detectTextSkewAngle(bmp)
                                    quadPoints = detected
                                    userRotationAngle = skew
                                    updatePreview()
                                }
                            }
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Auto AI", fontSize = 12.sp)
                        }
                    }

                    // Bottom Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                val result = previewBitmap ?: originalBitmap
                                if (result != null) {
                                    onApply(result)
                                }
                            },
                            enabled = !isProcessing && previewBitmap != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Apply & Save")
                        }
                    }
                }
            }
        }
    }
}
