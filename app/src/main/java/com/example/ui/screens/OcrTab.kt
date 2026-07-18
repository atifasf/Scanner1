package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.DocumentViewModel
import com.example.ui.ExportHelper
import com.example.ui.OCRHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrTab(viewModel: DocumentViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val selectedImageUri = viewModel.ocrImageUri
    val extractedText = viewModel.ocrExtractedText
    var isExtracting by remember { mutableStateOf(false) }
    var extractType by remember { mutableStateOf(if (viewModel.ocrIsTableSelected) "Table" else "Text") }

    // Synchronize local chip type with view model selection
    LaunchedEffect(viewModel.ocrIsTableSelected) {
        extractType = if (viewModel.ocrIsTableSelected) "Table" else "Text"
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.ocrImageUri = uri
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (selectedImageUri == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                    Text("Select Image for OCR")
                }
            }
        } else {
            AsyncImage(
                model = selectedImageUri,
                contentDescription = "Selected Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(bottom = 16.dp),
                contentScale = ContentScale.Fit
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                    Text("Change Image")
                }
                
                Button(
                    onClick = {
                        isExtracting = true
                        if (extractType == "Text") {
                            OCRHelper.extractText(context, selectedImageUri, "en", onSuccess = {
                                viewModel.ocrExtractedText = it
                                isExtracting = false
                            }, onError = {
                                viewModel.ocrExtractedText = "Failed: ${it.message}"
                                isExtracting = false
                            })
                        } else {
                            OCRHelper.extractTableAsCsv(context, selectedImageUri, onSuccess = {
                                viewModel.ocrExtractedText = it
                                isExtracting = false
                            }, onError = {
                                viewModel.ocrExtractedText = "Failed: ${it.message}"
                                isExtracting = false
                            })
                        }
                    },
                    enabled = !isExtracting
                ) {
                    if (isExtracting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Extract $extractType")
                    }
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                FilterChip(
                    selected = extractType == "Text",
                    onClick = { 
                        extractType = "Text"
                        viewModel.ocrIsTableSelected = false
                    },
                    label = { Text("Extract Text") }
                )
                Spacer(modifier = Modifier.width(16.dp))
                FilterChip(
                    selected = extractType == "Table",
                    onClick = { 
                        extractType = "Table"
                        viewModel.ocrIsTableSelected = true
                    },
                    label = { Text("Extract Table") }
                )
            }
        }

        if (extractedText.isNotEmpty()) {
            OutlinedTextField(
                value = extractedText,
                onValueChange = { viewModel.ocrExtractedText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .heightIn(min = 150.dp),
                label = { Text("Extracted Result") }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Export Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("OCR Text", extractedText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy", maxLines = 1)
                        }
                        
                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, extractedText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Text"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", maxLines = 1)
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                ExportHelper.exportToTxt(context, extractedText, "OCR_Export")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export TXT", maxLines = 1)
                        }
                        
                        Button(
                            onClick = {
                                ExportHelper.exportToWord(context, extractedText, "OCR_Export")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Word (.docx)", maxLines = 1)
                        }
                    }
                    
                    OutlinedButton(
                        onClick = {
                            ExportHelper.exportToExcel(context, extractedText, "OCR_Export")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Excel (.xlsx)", maxLines = 1)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(100.dp))
    }
}
