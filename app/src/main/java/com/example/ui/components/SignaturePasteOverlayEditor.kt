package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
import com.example.ui.ai.SavedSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignaturePasteOverlayEditor(
    documentFile: File,
    selectedSignature: SavedSignature,
    onDismiss: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var docBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sigBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Interactive Signature Transform State (Bitmap Coordinates)
    var sigOffsetX by remember { mutableFloatStateOf(0f) }
    var sigOffsetY by remember { mutableFloatStateOf(0f) }
    var sigScale by remember { mutableFloatStateOf(1.0f) }
    var sigRotation by remember { mutableFloatStateOf(0f) }
    var sigOpacity by remember { mutableFloatStateOf(1.0f) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Load document bitmap & signature bitmap
    LaunchedEffect(documentFile, selectedSignature) {
        withContext(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply { inMutable = true }
                val doc = BitmapFactory.decodeFile(documentFile.absolutePath, opts)
                val sig = BitmapFactory.decodeFile(selectedSignature.imagePath)

                docBitmap = doc
                sigBitmap = sig

                if (doc != null && sig != null) {
                    // Initial position: Bottom right center
                    sigOffsetX = doc.width * 0.55f
                    sigOffsetY = doc.height * 0.70f
                    // Initial scale: Fit around 25% width
                    val targetW = doc.width * 0.35f
                    sigScale = targetW / sig.width.toFloat()
                }
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
        Scaffold(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Paste Signature", fontWeight = FontWeight.Bold)
                        Text(selectedSignature.name, style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val doc = docBitmap ?: return@Button
                            val sig = sigBitmap ?: return@Button
                            isSaving = true

                            scope.launch(Dispatchers.IO) {
                                try {
                                    val resultBmp = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
                                    val canvas = AndroidCanvas(resultBmp)

                                    // 1. Draw base document
                                    canvas.drawBitmap(doc, 0f, 0f, null)

                                    // 2. Composite signature with scale, rotation, translation & opacity
                                    val matrix = Matrix().apply {
                                        postScale(sigScale, sigScale)
                                        postRotate(sigRotation, (sig.width * sigScale) / 2f, (sig.height * sigScale) / 2f)
                                        postTranslate(sigOffsetX, sigOffsetY)
                                    }

                                    val paint = AndroidPaint().apply {
                                        isAntiAlias = true
                                        isFilterBitmap = true
                                        alpha = (sigOpacity * 255).roundToInt().coerceIn(0, 255)
                                    }

                                    canvas.drawBitmap(sig, matrix, paint)

                                    // Save flattened result back to document file
                                    FileOutputStream(documentFile).use { out ->
                                        resultBmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                    }

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Signature applied & saved!", Toast.LENGTH_SHORT).show()
                                        onSaveSuccess()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Error saving signature", Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving && docBitmap != null && sigBitmap != null
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Apply & Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val doc = docBitmap
            val sig = sigBitmap

            if (doc != null && sig != null) {
                // Main Interactive Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.9f))
                        .onGloballyPositioned { containerSize = it.size },
                    contentAlignment = Alignment.Center
                ) {
                    val scaleX = if (containerSize.width > 0) containerSize.width.toFloat() / doc.width else 1f
                    val scaleY = if (containerSize.height > 0) containerSize.height.toFloat() / doc.height else 1f
                    val displayScale = minOf(scaleX, scaleY)

                    val offsetX = (containerSize.width - doc.width * displayScale) / 2f
                    val offsetY = (containerSize.height - doc.height * displayScale) / 2f

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(containerSize, displayScale) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    // Move signature in document bitmap coordinates
                                    sigOffsetX += dragAmount.x / displayScale
                                    sigOffsetY += dragAmount.y / displayScale
                                }
                            }
                    ) {
                        drawIntoCanvas { canvas ->
                            val nativeCanvas = canvas.nativeCanvas

                            // 1. Draw document
                            val docMatrix = Matrix().apply {
                                postScale(displayScale, displayScale)
                                postTranslate(offsetX, offsetY)
                            }
                            nativeCanvas.drawBitmap(doc, docMatrix, null)

                            // 2. Draw signature overlay
                            val sigMatrix = Matrix().apply {
                                postScale(sigScale * displayScale, sigScale * displayScale)
                                postRotate(
                                    sigRotation,
                                    (sig.width * sigScale * displayScale) / 2f,
                                    (sig.height * sigScale * displayScale) / 2f
                                )
                                postTranslate(
                                    sigOffsetX * displayScale + offsetX,
                                    sigOffsetY * displayScale + offsetY
                                )
                            }

                            val paint = AndroidPaint().apply {
                                isAntiAlias = true
                                isFilterBitmap = true
                                alpha = (sigOpacity * 255).roundToInt().coerceIn(0, 255)
                            }

                            nativeCanvas.drawBitmap(sig, sigMatrix, paint)
                        }
                    }
                }

                // Controls Panel
                Surface(
                    tonalElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            "Signature Adjustments (Drag to move)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Size / Scale Slider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AspectRatio, contentDescription = "Size", modifier = Modifier.size(20.dp))
                            Text("Size", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(50.dp))
                            Slider(
                                value = sigScale,
                                onValueChange = { sigScale = it },
                                valueRange = 0.2f..2.5f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${(sigScale * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                        }

                        // Rotation Slider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(20.dp))
                            Text("Rotate", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(50.dp))
                            Slider(
                                value = sigRotation,
                                onValueChange = { sigRotation = it },
                                valueRange = -180f..180f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${sigRotation.roundToInt()}°", style = MaterialTheme.typography.labelSmall)
                        }

                        // Opacity Slider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Opacity, contentDescription = "Opacity", modifier = Modifier.size(20.dp))
                            Text("Opacity", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(50.dp))
                            Slider(
                                value = sigOpacity,
                                onValueChange = { sigOpacity = it },
                                valueRange = 0.1f..1.0f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${(sigOpacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
    }
}
