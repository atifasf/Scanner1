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
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (doc.pdfPath != null) {
                                        if (doc.pdfPath.endsWith(".docx")) "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                        else "application/pdf"
                                    } else "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
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
}
