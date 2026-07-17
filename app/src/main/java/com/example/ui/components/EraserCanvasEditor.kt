package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EraseStroke(
    val points: List<Offset>, // In Bitmap coordinates
    val strokeWidth: Float    // In Bitmap coordinates
)

// Clean/Levels adjustment helper
private fun applyLevels(src: Bitmap, textDarkness: Float, backgroundClarity: Float): Bitmap {
    val width = src.width
    val height = src.height
    val pixels = IntArray(width * height)
    src.getPixels(pixels, 0, width, 0, 0, width, height)

    // L is low threshold (black point): e.g., 0 to 180
    // Increasing textDarkness pushes darker values to pure black
    val L = (textDarkness * 180f).toInt()
    
    // H is high threshold (white point): e.g., 255 down to 120
    // Increasing backgroundClarity pulls lighter values to pure white
    val H = (255f - (backgroundClarity * 135f)).toInt()
    
    val diff = (H - L).coerceAtLeast(1)

    // Precompute lookup table
    val lut = ByteArray(256)
    for (i in 0..255) {
        val newVal = when {
            i < L -> 0
            i > H -> 255
            else -> ((i - L) * 255 / diff).coerceIn(0, 255)
        }
        lut[i] = newVal.toByte()
    }

    for (i in pixels.indices) {
        val color = pixels[i]
        val a = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF

        val nr = lut[r].toInt() and 0xFF
        val ng = lut[g].toInt() and 0xFF
        val nb = lut[b].toInt() and 0xFF

        pixels[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
    }

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(pixels, 0, width, 0, 0, width, height)
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EraserCanvasEditor(
    imageFile: File,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var adjustedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    
    // Bottom bar tab selection: "clean" or "eraser"
    var activeTab by remember { mutableStateOf("clean") }
    
    // Adjustment sliders
    var textDarkness by remember { mutableFloatStateOf(0f) }
    var backgroundClarity by remember { mutableFloatStateOf(0f) }
    
    // Strokes drawn by the user (Bitmap coordinates)
    val strokes = remember { mutableStateListOf<EraseStroke>() }
    val undoStack = remember { mutableStateListOf<EraseStroke>() }
    
    // Current stroke being drawn (Bitmap coordinates)
    val currentStrokePoints = remember { mutableStateListOf<Offset>() }
    
    var brushSize by remember { mutableFloatStateOf(40f) } // Brush size in DP/Canvas pixels
    
    // Canvas dimensions
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }
    
    // Load original bitmap once
    LaunchedEffect(imageFile) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply {
                    inMutable = true // Ensure mutable bitmap
                }
                val loaded = BitmapFactory.decodeFile(imageFile.absolutePath, opts)
                originalBitmap = loaded
                adjustedBitmap = loaded
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isLoading = false
    }
    
    // Apply clean adjustment in background whenever sliders change
    LaunchedEffect(originalBitmap, textDarkness, backgroundClarity) {
        val src = originalBitmap ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val adjusted = if (textDarkness == 0f && backgroundClarity == 0f) {
                src
            } else {
                applyLevels(src, textDarkness, backgroundClarity)
            }
            adjustedBitmap = adjusted
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Erase & Clean", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (strokes.isNotEmpty()) {
                                val last = strokes.removeAt(strokes.size - 1)
                                undoStack.add(last)
                            }
                        },
                        enabled = strokes.isNotEmpty() && !isSaving
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    
                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                val last = undoStack.removeAt(undoStack.size - 1)
                                strokes.add(last)
                            }
                        },
                        enabled = undoStack.isNotEmpty() && !isSaving
                    ) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo")
                    }
                    
                    TextButton(
                        onClick = {
                            isSaving = true
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        originalBitmap?.let { b ->
                                            // Apply final clean levels
                                            val enhancedB = if (textDarkness > 0f || backgroundClarity > 0f) {
                                                applyLevels(b, textDarkness, backgroundClarity)
                                            } else {
                                                b.copy(Bitmap.Config.ARGB_8888, true)
                                            }
                                            
                                            // Draw eraser strokes on top of enhanced bitmap
                                            val canvas = AndroidCanvas(enhancedB)
                                            val paint = AndroidPaint().apply {
                                                color = android.graphics.Color.WHITE
                                                style = AndroidPaint.Style.STROKE
                                                strokeCap = AndroidPaint.Cap.ROUND
                                                strokeJoin = AndroidPaint.Join.ROUND
                                                isAntiAlias = true
                                            }
                                            
                                            strokes.forEach { stroke ->
                                                if (stroke.points.size > 1) {
                                                    paint.strokeWidth = stroke.strokeWidth
                                                    val path = AndroidPath()
                                                    path.moveTo(stroke.points[0].x, stroke.points[0].y)
                                                    for (i in 1 until stroke.points.size) {
                                                        path.lineTo(stroke.points[i].x, stroke.points[i].y)
                                                    }
                                                    canvas.drawPath(path, paint)
                                                } else if (stroke.points.size == 1) {
                                                    paint.strokeWidth = stroke.strokeWidth
                                                    canvas.drawPoint(stroke.points[0].x, stroke.points[0].y, paint)
                                                }
                                            }
                                            
                                            // Write back to file
                                            FileOutputStream(imageFile).use { out ->
                                                enhancedB.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                            }
                                            enhancedB.recycle()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                isSaving = false
                                onSave()
                            }
                        },
                        enabled = originalBitmap != null && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Text("Save", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Modern pill-shaped tab selector at the bottom
                    TabRow(
                        selectedTabIndex = if (activeTab == "clean") 0 else 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
                        indicator = {}, // Hide default indicator
                        divider = {}
                    ) {
                        Tab(
                            selected = activeTab == "clean",
                            onClick = { activeTab = "clean" },
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    if (activeTab == "clean") MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                ),
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        tint = if (activeTab == "clean") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Clean Page",
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeTab == "clean") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                        Tab(
                            selected = activeTab == "eraser",
                            onClick = { activeTab = "eraser" },
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    if (activeTab == "eraser") MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                ),
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Gesture,
                                        contentDescription = null,
                                        tint = if (activeTab == "eraser") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Eraser Tool",
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeTab == "eraser") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    if (activeTab == "clean") {
                        // 2 moving scrolls (sliders) for cleaning document
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Slider 1: Text Darkness
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Text Darkness (Darken Black)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "${(textDarkness * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = textDarkness,
                                    onValueChange = { textDarkness = it },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Slider 2: White Background Clarity
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                            imageVector = Icons.Default.BrightnessHigh,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "White Background Clarity",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "${(backgroundClarity * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = backgroundClarity,
                                    onValueChange = { backgroundClarity = it },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        // Eraser Tool options
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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
                                        imageVector = Icons.Default.Brush,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Brush Size: ${brushSize.toInt()}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                TextButton(
                                    onClick = {
                                        strokes.clear()
                                        undoStack.clear()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    enabled = strokes.isNotEmpty() && !isSaving
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clear Strokes", fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Slider(
                                value = brushSize,
                                onValueChange = { brushSize = it },
                                valueRange = 10f..150f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF1E1E1E)), // Dark immersive canvas background
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (adjustedBitmap == null) {
                Text("Failed to load image", color = Color.White)
            } else {
                val currentBitmap = adjustedBitmap!!
                val bitmapWidth = currentBitmap.width.toFloat()
                val bitmapHeight = currentBitmap.height.toFloat()
                
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .aspectRatio(bitmapWidth / bitmapHeight)
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            canvasWidth = coordinates.size.width.toFloat()
                            canvasHeight = coordinates.size.height.toFloat()
                        }
                        .clipToBounds()
                ) {
                    // Mapping logic helper
                    val scaleX = if (bitmapWidth > 0) canvasWidth / bitmapWidth else 1f
                    val scaleY = if (bitmapHeight > 0) canvasHeight / bitmapHeight else 1f
                    val scale = minOf(scaleX, scaleY)
                    
                    val dstWidth = bitmapWidth * scale
                    val dstHeight = bitmapHeight * scale
                    
                    val offsetX = (canvasWidth - dstWidth) / 2f
                    val offsetY = (canvasHeight - dstHeight) / 2f
                    
                    val imageBitmap = remember(currentBitmap) { currentBitmap.asImageBitmap() }
                    
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(canvasWidth, canvasHeight) {
                                detectDragGestures(
                                    onDragStart = { pointer ->
                                        // Drawing eraser strokes is ONLY allowed if activeTab is "eraser"
                                        if (activeTab == "eraser") {
                                            val xMapped = (pointer.x - offsetX) / scale
                                            val yMapped = (pointer.y - offsetY) / scale
                                            currentStrokePoints.clear()
                                            currentStrokePoints.add(Offset(xMapped, yMapped))
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        if (activeTab == "eraser") {
                                            change.consume()
                                            val currentPos = change.position
                                            val xMapped = (currentPos.x - offsetX) / scale
                                            val yMapped = (currentPos.y - offsetY) / scale
                                            currentStrokePoints.add(Offset(xMapped, yMapped))
                                        }
                                    },
                                    onDragEnd = {
                                        if (activeTab == "eraser" && currentStrokePoints.isNotEmpty()) {
                                            val brushSizeInBitmap = brushSize / scale
                                            strokes.add(
                                                EraseStroke(
                                                    points = currentStrokePoints.toList(),
                                                    strokeWidth = brushSizeInBitmap
                                                )
                                            )
                                            undoStack.clear()
                                            currentStrokePoints.clear()
                                        }
                                    }
                                )
                            }
                    ) {
                        // 1. Draw the document bitmap (fully reactive to adjustments)
                        drawImage(
                            image = imageBitmap,
                            dstSize = androidx.compose.ui.unit.IntSize(dstWidth.toInt(), dstHeight.toInt()),
                            dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt())
                        )
                        
                        // Helper to map bitmap coordinates back to canvas screen coordinates
                        fun mapToCanvas(offset: Offset): Offset {
                            return Offset(
                                x = offset.x * scale + offsetX,
                                y = offset.y * scale + offsetY
                            )
                        }
                        
                        // 2. Draw historical strokes (white lines)
                        strokes.forEach { stroke ->
                            if (stroke.points.size > 1) {
                                val path = Path()
                                val start = mapToCanvas(stroke.points[0])
                                path.moveTo(start.x, start.y)
                                for (i in 1 until stroke.points.size) {
                                    val point = mapToCanvas(stroke.points[i])
                                    path.lineTo(point.x, point.y)
                                }
                                drawPath(
                                    path = path,
                                    color = Color.White,
                                    style = Stroke(
                                        width = stroke.strokeWidth * scale,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            } else if (stroke.points.size == 1) {
                                val point = mapToCanvas(stroke.points[0])
                                drawCircle(
                                    color = Color.White,
                                    radius = (stroke.strokeWidth * scale) / 2f,
                                    center = point
                                )
                            }
                        }
                        
                        // 3. Draw current active stroke
                        if (currentStrokePoints.size > 1) {
                            val path = Path()
                            val start = mapToCanvas(currentStrokePoints[0])
                            path.moveTo(start.x, start.y)
                            for (i in 1 until currentStrokePoints.size) {
                                val point = mapToCanvas(currentStrokePoints[i])
                                path.lineTo(point.x, point.y)
                            }
                            drawPath(
                                path = path,
                                color = Color.White,
                                style = Stroke(
                                    width = brushSize,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        } else if (currentStrokePoints.size == 1) {
                            val point = mapToCanvas(currentStrokePoints[0])
                            drawCircle(
                                color = Color.White,
                                radius = brushSize / 2f,
                                center = point
                            )
                        }
                    }
                }
            }
        }
    }
}
