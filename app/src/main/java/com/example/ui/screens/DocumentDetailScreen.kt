package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.DocumentEntity
import com.example.ui.DocumentViewModel
import com.example.ui.OCRHelper
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    documentId: String,
    viewModel: DocumentViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var document by remember { mutableStateOf<DocumentEntity?>(null) }
    var isOcrLoading by remember { mutableStateOf(false) }

    LaunchedEffect(documentId) {
        document = viewModel.getDocumentById(documentId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document?.name ?: "Document") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        document?.let {
                            viewModel.toggleFavorite(it)
                            document = it.copy(isFavorite = !it.isFavorite)
                        }
                    }) {
                        Icon(
                            if (document?.isFavorite == true) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite"
                        )
                    }
                    IconButton(onClick = {
                        document?.let {
                            val file = it.pdfPath?.let { path -> File(path) } 
                                ?: it.imagePaths.split(",").firstOrNull()?.let { path -> File(path) }
                            if (file != null && file.exists()) {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (it.pdfPath != null) "application/pdf" else "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { padding ->
        if (document == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                val imagePaths = document!!.imagePaths.split(",").filter { it.isNotEmpty() }
                if (imagePaths.isNotEmpty()) {
                    AsyncImage(
                        model = File(imagePaths.first()),
                        contentDescription = "Document preview",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            if (imagePaths.isNotEmpty()) {
                                isOcrLoading = true
                                val uri = android.net.Uri.fromFile(File(imagePaths.first()))
                                OCRHelper.extractText(context, uri,
                                    onSuccess = { text ->
                                        isOcrLoading = false
                                        val updatedDoc = document!!.copy(ocrText = text)
                                        viewModel.updateDocument(updatedDoc)
                                        document = updatedDoc
                                    },
                                    onError = {
                                        isOcrLoading = false
                                    }
                                )
                            }
                        },
                        enabled = !isOcrLoading
                    ) {
                        if (isOcrLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.TextFields, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extract Text")
                        }
                    }
                }

                if (!document!!.ocrText.isNullOrEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Extracted Text", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(document!!.ocrText!!)
                        }
                    }
                }
            }
        }
    }
}
