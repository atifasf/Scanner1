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
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var sharpness by remember { mutableFloatStateOf(0f) }
    
    // Strokes drawn by the user (Bitmap coordinates)
    val strokes = remember { mutableStateListOf<EraseStroke>() }
    val undoStack = remember { mutableStateListOf<EraseStroke>() }
    
    // Current stroke being drawn (Bitmap coordinates)
    val currentStrokePoints = remember { mutableStateListOf<Offset>() }
    
    var brushSize by remember { mutableFloatStateOf(40f) } // Brush size in DP/Canvas pixels
    
    // Zoom & Pan state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var activePointerPos by remember { mutableStateOf<Offset?>(null) }
    
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
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
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

                                            // Apply sharpness if needed
                                            var finalEnhancedB = enhancedB
                                            if (sharpness > 0f) {
                                                finalEnhancedB = com.example.ui.ImageEnhancer.deblurAndSharpenText(enhancedB, sharpness * 1.5f)
                                                if (finalEnhancedB != enhancedB) enhancedB.recycle()
                                            }
                                            
                                            // 2. Draw user's eraser strokes directly on top (total erase white canvas overlay)
                                            val finalCanvas = AndroidCanvas(finalEnhancedB)
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
                                                    finalCanvas.drawPath(path, strokePaint)
                                                } else if (stroke.points.size == 1) {
                                                    strokePaint.strokeWidth = stroke.strokeWidth
                                                    finalCanvas.drawPoint(stroke.points[0].x, stroke.points[0].y, strokePaint)
                                                }
                                            }
                                            
                                            // Write back to file
                                            FileOutputStream(imageFile).use { out ->
                                                finalEnhancedB.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                            }
                                            finalEnhancedB.recycle()
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
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .heightIn(max = 210.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
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
                                zoomScale = 1f
                                panOffsetX = 0f
                                panOffsetY = 0f
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            enabled = (strokes.isNotEmpty() || textDarkness > 0f || backgroundClarity > 0f) && !isSaving
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset All", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Slider 1: Eraser Brush Size
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Brush,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Erase Brush Size",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${brushSize.toInt()}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = brushSize,
                            onValueChange = { brushSize = it },
                            valueRange = 10f..150f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                        )
                    }

                    // Slider 2: Text Darkness
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Text Darkness (Darken Black)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${(textDarkness * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = textDarkness,
                            onValueChange = { textDarkness = it },
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                        )
                    }

                    // Slider 3: White Background Clarity
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BrightnessHigh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "White Background Clarity",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${(backgroundClarity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = backgroundClarity,
                            onValueChange = { backgroundClarity = it },
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                        )
                    }

                    // Slider 4: Image Sharpness
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Details,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Edge Sharpness",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${(sharpness * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = sharpness,
                            onValueChange = { sharpness = it },
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
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
                    val fitScaleX = if (bitmapWidth > 0) canvasWidth / bitmapWidth else 1f
                    val fitScaleY = if (bitmapHeight > 0) canvasHeight / bitmapHeight else 1f
                    val fitScale = minOf(fitScaleX, fitScaleY)
                    
                    val fitWidth = bitmapWidth * fitScale
                    val fitHeight = bitmapHeight * fitScale
                    
                    val fitOffsetX = (canvasWidth - fitWidth) / 2f
                    val fitOffsetY = (canvasHeight - fitHeight) / 2f
                    
                    val imageBitmap = remember(currentBitmap) { currentBitmap.asImageBitmap() }
                    
                    var isMultiTouchGesture by remember { mutableStateOf(false) }
                    
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(canvasWidth, canvasHeight, fitScale, fitOffsetX, fitOffsetY, zoomScale, panOffsetX, panOffsetY, brushSize) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressedChanges = event.changes.filter { it.pressed }

                                        if (pressedChanges.size >= 2) {
                                            // 2 or more fingers: Zoom & Pan mode!
                                            if (currentStrokePoints.isNotEmpty()) {
                                                if (currentStrokePoints.size > 1) {
                                                    val strokeWidthInBitmap = brushSize / (fitScale * zoomScale)
                                                    strokes.add(
                                                        EraseStroke(
                                                            points = currentStrokePoints.toList(),
                                                            strokeWidth = strokeWidthInBitmap
                                                        )
                                                    )
                                                    undoStack.clear()
                                                }
                                                currentStrokePoints.clear()
                                            }
                                            isMultiTouchGesture = true

                                            val currentCentroid = event.calculateCentroid()
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()

                                            if (currentCentroid != Offset.Unspecified) {
                                                val newZoom = (zoomScale * zoomChange).coerceIn(1f, 10f)
                                                if (newZoom <= 1.01f) {
                                                    zoomScale = 1f
                                                    panOffsetX = 0f
                                                    panOffsetY = 0f
                                                } else {
                                                    val maxPanX = (canvasWidth * (newZoom - 1f)) / 2f + canvasWidth * 0.4f
                                                    val maxPanY = (canvasHeight * (newZoom - 1f)) / 2f + canvasHeight * 0.4f
                                                    
                                                    val newPanX = (panOffsetX + panChange.x).coerceIn(-maxPanX, maxPanX)
                                                    val newPanY = (panOffsetY + panChange.y).coerceIn(-maxPanY, maxPanY)
                                                    
                                                    zoomScale = newZoom
                                                    panOffsetX = newPanX
                                                    panOffsetY = newPanY
                                                }
                                            }

                                            pressedChanges.forEach { it.consume() }
                                            activePointerPos = null
                                        } else if (pressedChanges.size == 1) {
                                            val change = pressedChanges[0]
                                            if (isMultiTouchGesture) {
                                                // Waiting for all fingers to lift after gesture
                                                change.consume()
                                                activePointerPos = null
                                            } else {
                                                // 1 finger: Erase mode!
                                                val touchPos = change.position
                                                activePointerPos = touchPos

                                                // Inverse transform from Screen/Canvas to Bitmap space
                                                val xZoomed = touchPos.x - panOffsetX
                                                val yZoomed = touchPos.y - panOffsetY
                                                val pivotX = canvasWidth / 2f
                                                val pivotY = canvasHeight / 2f

                                                val xFit = (xZoomed - pivotX) / zoomScale + pivotX
                                                val yFit = (yZoomed - pivotY) / zoomScale + pivotY

                                                val xBmp = (xFit - fitOffsetX) / fitScale
                                                val yBmp = (yFit - fitOffsetY) / fitScale

                                                val mappedPoint = Offset(xBmp, yBmp)

                                                if (change.changedToDown()) {
                                                    currentStrokePoints.clear()
                                                    currentStrokePoints.add(mappedPoint)
                                                } else if (change.positionChanged()) {
                                                    currentStrokePoints.add(mappedPoint)
                                                }

                                                change.consume()
                                            }
                                        } else {
                                            // All pointers lifted
                                            if (isMultiTouchGesture) {
                                                isMultiTouchGesture = false
                                            }
                                            if (currentStrokePoints.isNotEmpty()) {
                                                val strokeWidthInBitmap = brushSize / (fitScale * zoomScale)
                                                strokes.add(
                                                    EraseStroke(
                                                        points = currentStrokePoints.toList(),
                                                        strokeWidth = strokeWidthInBitmap
                                                    )
                                                )
                                                undoStack.clear()
                                                currentStrokePoints.clear()
                                            }
                                            activePointerPos = null
                                        }
                                    }
                                }
                            }
                    ) {
                        // Hardware accelerated matrix transform
                        withTransform({
                            translate(left = panOffsetX, top = panOffsetY)
                            scale(scaleX = zoomScale, scaleY = zoomScale, pivot = Offset(canvasWidth / 2f, canvasHeight / 2f))
                        }) {
                            // 1. Draw Document Bitmap
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
                                dstSize = androidx.compose.ui.unit.IntSize(fitWidth.toInt(), fitHeight.toInt()),
                                dstOffset = androidx.compose.ui.unit.IntOffset(fitOffsetX.toInt(), fitOffsetY.toInt()),
                                colorFilter = ColorFilter.colorMatrix(matrix)
                            )

                            // Helper to map bitmap coordinates to unzoomed Canvas coordinates
                            fun mapToUnzoomedCanvas(offset: Offset): Offset {
                                return Offset(
                                    x = offset.x * fitScale + fitOffsetX,
                                    y = offset.y * fitScale + fitOffsetY
                                )
                            }

                            // 2. Draw Historical Erase Strokes
                            strokes.forEach { stroke ->
                                val canvasStrokeWidth = stroke.strokeWidth * fitScale
                                if (stroke.points.size > 1) {
                                    val path = Path()
                                    val start = mapToUnzoomedCanvas(stroke.points[0])
                                    path.moveTo(start.x, start.y)
                                    for (i in 1 until stroke.points.size) {
                                        val point = mapToUnzoomedCanvas(stroke.points[i])
                                        path.lineTo(point.x, point.y)
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color.White,
                                        style = Stroke(
                                            width = canvasStrokeWidth,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                } else if (stroke.points.size == 1) {
                                    val point = mapToUnzoomedCanvas(stroke.points[0])
                                    drawCircle(
                                        color = Color.White,
                                        radius = canvasStrokeWidth / 2f,
                                        center = point
                                    )
                                }
                            }

                            // 3. Draw Active Stroke
                            if (currentStrokePoints.isNotEmpty()) {
                                val activeCanvasWidth = brushSize / zoomScale
                                if (currentStrokePoints.size > 1) {
                                    val path = Path()
                                    val start = mapToUnzoomedCanvas(currentStrokePoints[0])
                                    path.moveTo(start.x, start.y)
                                    for (i in 1 until currentStrokePoints.size) {
                                        val point = mapToUnzoomedCanvas(currentStrokePoints[i])
                                        path.lineTo(point.x, point.y)
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color.White,
                                        style = Stroke(
                                            width = activeCanvasWidth,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                } else if (currentStrokePoints.size == 1) {
                                    val point = mapToUnzoomedCanvas(currentStrokePoints[0])
                                    drawCircle(
                                        color = Color.White,
                                        radius = activeCanvasWidth / 2f,
                                        center = point
                                    )
                                }
                            }
                        }
                    }

                    // Floating Live Eraser Cursor Circle Overlay (Screen space)
                    activePointerPos?.let { pos ->
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color(0xCCFF3D00),
                                radius = brushSize / 2f,
                                center = pos,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White,
                                radius = brushSize / 2f - 1.dp.toPx(),
                                center = pos,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }

                    // Floating Zoom Info Badge and Reset Zoom Control
                    if (zoomScale > 1.05f) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.8f),
                            contentColor = Color.White,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${(zoomScale * 100).toInt()}% Zoom",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Reset",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            zoomScale = 1f
                                            panOffsetX = 0f
                                            panOffsetY = 0f
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
