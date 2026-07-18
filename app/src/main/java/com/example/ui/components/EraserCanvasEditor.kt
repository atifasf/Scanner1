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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EraserCanvasEditor(
    imageFile: File,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    
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
                bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, opts)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isLoading = false
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Erase & Clean Tool", fontWeight = FontWeight.Bold) },
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
                                        bitmap?.let { b ->
                                            // 1. Create mutable bitmap with real-time levels applied natively on background thread (virtually instant)
                                            val enhancedB = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
                                            val canvas = AndroidCanvas(enhancedB)
                                            val paint = AndroidPaint().apply {
                                                isAntiAlias = true
                                            }
                                            
                                            if (textDarkness > 0f || backgroundClarity > 0f) {
                                                val c = 1f + textDarkness * 1.5f
                                                val bVal = 128f * (1f - c) + backgroundClarity * 150f
                                                val matrix = android.graphics.ColorMatrix(
                                                    floatArrayOf(
                                                        c, 0f, 0f, 0f, bVal,
                                                        0f, c, 0f, 0f, bVal,
                                                        0f, 0f, c, 0f, bVal,
                                                        0f, 0f, 0f, 1f, 0f
                                                    )
                                                )
                                                paint.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
                                            }
                                            
                                            canvas.drawBitmap(b, 0f, 0f, paint)
                                            
                                            // 2. Draw user's eraser strokes directly on top (total erase white canvas overlay)
                                            val strokePaint = AndroidPaint().apply {
                                                color = android.graphics.Color.WHITE
                                                style = AndroidPaint.Style.STROKE
                                                strokeCap = AndroidPaint.Cap.ROUND
                                                strokeJoin = AndroidPaint.Join.ROUND
                                                isAntiAlias = true
                                            }
                                            
                                            strokes.forEach { stroke ->
                                                if (stroke.points.size > 1) {
                                                    strokePaint.strokeWidth = stroke.strokeWidth
                                                    val path = AndroidPath()
                                                    path.moveTo(stroke.points[0].x, stroke.points[0].y)
                                                    for (i in 1 until stroke.points.size) {
                                                        path.lineTo(stroke.points[i].x, stroke.points[i].y)
                                                    }
                                                    canvas.drawPath(path, strokePaint)
                                                } else if (stroke.points.size == 1) {
                                                    strokePaint.strokeWidth = stroke.strokeWidth
                                                    canvas.drawPoint(stroke.points[0].x, stroke.points[0].y, strokePaint)
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
                        enabled = bitmap != null && !isSaving
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header controls with a Reset Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Eraser & Clean Controls",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        TextButton(
                            onClick = {
                                strokes.clear()
                                undoStack.clear()
                                textDarkness = 0f
                                backgroundClarity = 0f
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            enabled = (strokes.isNotEmpty() || textDarkness > 0f || backgroundClarity > 0f) && !isSaving
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset All", fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Slider 1: Eraser Brush Size
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Erase Brush Size",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${brushSize.toInt()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = brushSize,
                            onValueChange = { brushSize = it },
                            valueRange = 10f..150f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Slider 2: Text Darkness
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
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

                    // Slider 3: White Background Clarity
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
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
            } else if (bitmap == null) {
                Text("Failed to load image", color = Color.White)
            } else {
                val currentBitmap = bitmap!!
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
                                        // Drawing eraser strokes is ALWAYS allowed!
                                        val xMapped = (pointer.x - offsetX) / scale
                                        val yMapped = (pointer.y - offsetY) / scale
                                        currentStrokePoints.clear()
                                        currentStrokePoints.add(Offset(xMapped, yMapped))
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val currentPos = change.position
                                        val xMapped = (currentPos.x - offsetX) / scale
                                        val yMapped = (currentPos.y - offsetY) / scale
                                        currentStrokePoints.add(Offset(xMapped, yMapped))
                                    },
                                    onDragEnd = {
                                        if (currentStrokePoints.isNotEmpty()) {
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
                        // 1. Draw the document bitmap applying GPU acceleration on-the-fly!
                        val c = 1f + textDarkness * 1.5f
                        val bVal = 128f * (1f - c) + backgroundClarity * 150f
                        val matrix = ColorMatrix(
                            floatArrayOf(
                                c, 0f, 0f, 0f, bVal,
                                0f, c, 0f, 0f, bVal,
                                0f, 0f, c, 0f, bVal,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                        
                        drawImage(
                            image = imageBitmap,
                            dstSize = androidx.compose.ui.unit.IntSize(dstWidth.toInt(), dstHeight.toInt()),
                            dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                            colorFilter = ColorFilter.colorMatrix(matrix)
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
