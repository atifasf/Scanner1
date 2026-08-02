package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.DocumentEntity
import com.example.ui.ai.MagicScanResult
import com.example.ui.ai.PdfCompressionPreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicScanComparisonDialog(
    magicResult: MagicScanResult,
    duplicateMatchedDoc: DocumentEntity?,
    duplicateSimilarity: Int,
    onDismiss: () -> Unit,
    onSave: (
        finalBitmap: Bitmap,
        fileName: String,
        compressionPreset: PdfCompressionPreset,
        password: String?,
        duplicateAction: String // "KEEP_BOTH", "REPLACE", "SKIP"
    ) -> Unit
) {
    var showOriginal by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf(magicResult.suggestedFileName) }
    var selectedPreset by remember { mutableStateOf(PdfCompressionPreset.BALANCED) }
    var pdfPassword by remember { mutableStateOf("") }
    var showPasswordInput by remember { mutableStateOf(false) }
    var userDuplicateAction by remember { mutableStateOf("KEEP_BOTH") }

    // Text Darkness & White Background Clarity Filter Controls
    var textDarkness by remember { mutableFloatStateOf(0f) }
    var backgroundClarity by remember { mutableFloatStateOf(0f) }
    var sharpness by remember { mutableFloatStateOf(0f) }

    val processedEnhancedBitmap = remember(magicResult.enhancedBitmap, textDarkness, backgroundClarity, sharpness) {
        if (textDarkness > 0f || backgroundClarity > 0f || sharpness > 0f) {
            com.example.ui.ImageEnhancer.applyImageAdjustments(
                magicResult.enhancedBitmap,
                textDarkness,
                backgroundClarity,
                sharpness
            )
        } else {
            magicResult.enhancedBitmap
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
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Header with Magic Scan Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Magic Scan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Magic Scan Processed",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Shadow & finger removal applied",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Before vs After Preview Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    val currentDisplayBmp = if (showOriginal) magicResult.originalBitmap else processedEnhancedBitmap

                    Image(
                        bitmap = currentDisplayBmp.asImageBitmap(),
                        contentDescription = if (showOriginal) "Original Image" else "Magic Enhanced Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // Before/After Toggle Floating Switch
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        FilterChip(
                            selected = showOriginal,
                            onClick = { showOriginal = !showOriginal },
                            label = {
                                Text(
                                    text = if (showOriginal) "Showing Original" else "Showing Magic Scan",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (showOriginal) Icons.Default.FlipToBack else Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                labelColor = Color.White,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Settings & Options Scrollable Sheet
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Duplicate Warning Card
                    if (duplicateMatchedDoc != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.FileCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                    Text(
                                        text = "Duplicate Scan Detected ($duplicateSimilarity% Match)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Text(
                                    text = "Matches existing document '${duplicateMatchedDoc.name}'. Choose an action:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("KEEP_BOTH" to "Keep Both", "REPLACE" to "Replace", "SKIP" to "Skip").forEach { (action, label) ->
                                        val isSel = userDuplicateAction == action
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { userDuplicateAction = action },
                                            label = { Text(label, fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Smart Auto File Naming Input
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("Smart Auto File Name") },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            Text(
                                text = "AI Suggested",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Document Filters: Text Darkness & Background Clarity
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Scan Enhancement Filters",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Text Darkness Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Text Darkness (Darken Black)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${(textDarkness * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = textDarkness,
                                onValueChange = { textDarkness = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth().height(28.dp)
                            )

                            // White Background Clarity Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "White Background Clarity",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${(backgroundClarity * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = backgroundClarity,
                                onValueChange = { backgroundClarity = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth().height(28.dp)
                            )
                            
                            // Image Sharpness Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Edge Sharpness (Reduce Blur)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${(sharpness * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = sharpness,
                                onValueChange = { sharpness = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth().height(28.dp)
                            )
                        }
                    }

                    // PDF Size Optimizer Preset Selection
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("AI PDF Size Optimizer", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PdfCompressionPreset.values().forEach { preset ->
                                val isSel = selectedPreset == preset
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedPreset = preset },
                                    label = { Text(preset.displayName, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Password Protection Input
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Password Protect PDF", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                            Switch(
                                checked = showPasswordInput,
                                onCheckedChange = {
                                    showPasswordInput = it
                                    if (!it) pdfPassword = ""
                                }
                            )
                        }

                        AnimatedVisibility(visible = showPasswordInput) {
                            OutlinedTextField(
                                value = pdfPassword,
                                onValueChange = { pdfPassword = it },
                                label = { Text("Set PDF Password") },
                                placeholder = { Text("e.g. Pass123!") },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            onSave(
                                processedEnhancedBitmap,
                                fileName,
                                selectedPreset,
                                if (showPasswordInput && pdfPassword.isNotBlank()) pdfPassword else null,
                                userDuplicateAction
                            )
                        },
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Document")
                    }
                }
            }
        }
    }
}
