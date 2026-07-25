package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun SignatureCropEditor(
    documentBitmap: Bitmap,
    onExtract: (android.graphics.Rect) -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportScale by remember { mutableFloatStateOf(1f) }
    var viewportOffset by remember { mutableStateOf(Offset.Zero) }

    var cropRect by remember {
        val w = documentBitmap.width.toFloat()
        val h = documentBitmap.height.toFloat()
        mutableStateOf(Rect(w * 0.1f, h * 0.6f, w * 0.9f, h * 0.95f))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
                .onGloballyPositioned { containerSize = it.size }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        viewportScale = (viewportScale * zoom).coerceIn(1f, 5f)
                        viewportOffset += pan
                    }
                }
        ) {
            if (containerSize != IntSize.Zero) {
                val bmpW = documentBitmap.width.toFloat()
                val bmpH = documentBitmap.height.toFloat()
                val displayScale = minOf(containerSize.width / bmpW, containerSize.height / bmpH) * viewportScale
                val offsetX = (containerSize.width - bmpW * displayScale) / 2f + viewportOffset.x
                val offsetY = (containerSize.height - bmpH * displayScale) / 2f + viewportOffset.y

                fun imgToScreenX(imgX: Float) = imgX * displayScale + offsetX
                fun imgToScreenY(imgY: Float) = imgY * displayScale + offsetY

                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw Image
                    drawImage(
                        image = documentBitmap.asImageBitmap(),
                        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        srcSize = androidx.compose.ui.unit.IntSize(bmpW.toInt(), bmpH.toInt()),
                        dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize((bmpW * displayScale).toInt(), (bmpH * displayScale).toInt())
                    )

                    // Draw Dark Overlay with Cutout
                    val rectL = imgToScreenX(cropRect.left)
                    val rectT = imgToScreenY(cropRect.top)
                    val rectR = imgToScreenX(cropRect.right)
                    val rectB = imgToScreenY(cropRect.bottom)

                    drawRect(Color.Black.copy(alpha = 0.5f))
                    drawRect(
                        color = Color.Transparent,
                        topLeft = Offset(rectL, rectT),
                        size = Size(rectR - rectL, rectB - rectT),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                    )
                    
                    // Draw Crop Box Border
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(rectL, rectT),
                        size = Size(rectR - rectL, rectB - rectT),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Drag Handle Modifier Factory
                fun Modifier.cropHandle(
                    isLeft: Boolean, isTop: Boolean, isRight: Boolean, isBottom: Boolean, isMove: Boolean = false
                ) = this.pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val imgDragX = dragAmount.x / displayScale
                        val imgDragY = dragAmount.y / displayScale
                        
                        var newL = cropRect.left
                        var newT = cropRect.top
                        var newR = cropRect.right
                        var newB = cropRect.bottom
                        
                        if (isMove) {
                            newL += imgDragX; newR += imgDragX
                            newT += imgDragY; newB += imgDragY
                        } else {
                            if (isLeft) newL += imgDragX
                            if (isTop) newT += imgDragY
                            if (isRight) newR += imgDragX
                            if (isBottom) newB += imgDragY
                        }
                        
                        if (newR - newL < 50f) {
                            if (isLeft) newL = newR - 50f
                            if (isRight) newR = newL + 50f
                        }
                        if (newB - newT < 50f) {
                            if (isTop) newT = newB - 50f
                            if (isBottom) newB = newT + 50f
                        }
                        
                        if (isMove) {
                            val w = newR - newL
                            val h = newB - newT
                            newL = newL.coerceIn(0f, bmpW - w)
                            newT = newT.coerceIn(0f, bmpH - h)
                            newR = newL + w
                            newB = newT + h
                        } else {
                            newL = max(0f, newL)
                            newR = minOf(newR, bmpW)
                            newT = max(0f, newT)
                            newB = minOf(newB, bmpH)
                        }
                        
                        cropRect = Rect(newL, newT, newR, newB)
                    }
                }

                val rectL = imgToScreenX(cropRect.left)
                val rectT = imgToScreenY(cropRect.top)
                val rectR = imgToScreenX(cropRect.right)
                val rectB = imgToScreenY(cropRect.bottom)
                val handleSize = 48.dp

                // Center Move Area
                Box(modifier = Modifier
                    .offset { IntOffset(rectL.toInt(), rectT.toInt()) }
                    .size(with(androidx.compose.ui.platform.LocalDensity.current) { (rectR - rectL).toDp() }, with(androidx.compose.ui.platform.LocalDensity.current) { (rectB - rectT).toDp() })
                    .cropHandle(false, false, false, false, true)
                )

                // Corners
                Box(modifier = Modifier.offset { IntOffset((rectL - handleSize.toPx()/2).toInt(), (rectT - handleSize.toPx()/2).toInt()) }.size(handleSize).cropHandle(true, true, false, false))
                Box(modifier = Modifier.offset { IntOffset((rectR - handleSize.toPx()/2).toInt(), (rectT - handleSize.toPx()/2).toInt()) }.size(handleSize).cropHandle(false, true, true, false))
                Box(modifier = Modifier.offset { IntOffset((rectL - handleSize.toPx()/2).toInt(), (rectB - handleSize.toPx()/2).toInt()) }.size(handleSize).cropHandle(true, false, false, true))
                Box(modifier = Modifier.offset { IntOffset((rectR - handleSize.toPx()/2).toInt(), (rectB - handleSize.toPx()/2).toInt()) }.size(handleSize).cropHandle(false, false, true, true))
                
                // Edges
                Box(modifier = Modifier.offset { IntOffset((rectL + handleSize.toPx()/2).toInt(), (rectT - handleSize.toPx()/2).toInt()) }.size(with(androidx.compose.ui.platform.LocalDensity.current) { (rectR - rectL - handleSize.toPx()).toDp() }, handleSize).cropHandle(false, true, false, false))
                Box(modifier = Modifier.offset { IntOffset((rectL + handleSize.toPx()/2).toInt(), (rectB - handleSize.toPx()/2).toInt()) }.size(with(androidx.compose.ui.platform.LocalDensity.current) { (rectR - rectL - handleSize.toPx()).toDp() }, handleSize).cropHandle(false, false, false, true))
                Box(modifier = Modifier.offset { IntOffset((rectL - handleSize.toPx()/2).toInt(), (rectT + handleSize.toPx()/2).toInt()) }.size(handleSize, with(androidx.compose.ui.platform.LocalDensity.current) { (rectB - rectT - handleSize.toPx()).toDp() }).cropHandle(true, false, false, false))
                Box(modifier = Modifier.offset { IntOffset((rectR - handleSize.toPx()/2).toInt(), (rectT + handleSize.toPx()/2).toInt()) }.size(handleSize, with(androidx.compose.ui.platform.LocalDensity.current) { (rectB - rectT - handleSize.toPx()).toDp() }).cropHandle(false, false, true, false))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val androidRect = android.graphics.Rect(
                    cropRect.left.toInt(),
                    cropRect.top.toInt(),
                    cropRect.right.toInt(),
                    cropRect.bottom.toInt()
                )
                onExtract(androidRect)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Extract Selected Area")
        }
    }
}
