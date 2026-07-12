package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.DocumentViewModel
import com.example.ui.OCRHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrTab(viewModel: DocumentViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var isExtracting by remember { mutableStateOf(false) }
    var extractType by remember { mutableStateOf("Text") } // Text, Table

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
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
                            OCRHelper.extractText(context, selectedImageUri!!, "en", onSuccess = {
                                extractedText = it
                                isExtracting = false
                            }, onError = {
                                extractedText = "Failed: ${it.message}"
                                isExtracting = false
                            })
                        } else {
                            OCRHelper.extractTableAsCsv(context, selectedImageUri!!, onSuccess = {
                                extractedText = it
                                isExtracting = false
                            }, onError = {
                                extractedText = "Failed: ${it.message}"
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
                    onClick = { extractType = "Text" },
                    label = { Text("Extract Text") }
                )
                Spacer(modifier = Modifier.width(16.dp))
                FilterChip(
                    selected = extractType == "Table",
                    onClick = { extractType = "Table" },
                    label = { Text("Extract Table") }
                )
            }
        }

        if (extractedText.isNotEmpty()) {
            OutlinedTextField(
                value = extractedText,
                onValueChange = { extractedText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .heightIn(min = 150.dp),
                label = { Text("Extracted Result") }
            )
            
            Button(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("OCR Text", extractedText)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth()
            ) {
                Text("Copy to Clipboard")
            }
        }
        
        Spacer(modifier = Modifier.height(100.dp))
    }
}
