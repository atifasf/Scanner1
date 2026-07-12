package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAdmin: () -> Unit = {}
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    
    var isBiometricEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("biometric_enabled", true)) }
    var isDarkTheme by remember { mutableStateOf(sharedPrefs.getBoolean("dark_theme", false)) }
    var autoOcr by remember { mutableStateOf(sharedPrefs.getBoolean("auto_ocr", false)) }
    var ocrLanguage by remember { mutableStateOf(sharedPrefs.getString("ocr_language", "en") ?: "en") }
    var showLanguageDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 8.dp))
            ListItem(
                headlineContent = { Text("Dark Theme") },
                trailingContent = {
                    Switch(checked = isDarkTheme, onCheckedChange = { 
                        isDarkTheme = it
                        sharedPrefs.edit().putBoolean("dark_theme", it).apply()
                    })
                }
            )
            HorizontalDivider()

            Text("Security", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 8.dp))
            ListItem(
                headlineContent = { Text("Biometric Authentication") },
                supportingContent = { Text("Require unlock on open") },
                trailingContent = {
                    Switch(checked = isBiometricEnabled, onCheckedChange = { 
                        isBiometricEnabled = it
                        sharedPrefs.edit().putBoolean("biometric_enabled", it).apply()
                    })
                }
            )
            HorizontalDivider()
            
            Text("OCR Settings", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 8.dp))
            ListItem(
                headlineContent = { Text("Auto OCR after Scan") },
                trailingContent = {
                    Switch(checked = autoOcr, onCheckedChange = { 
                        autoOcr = it
                        sharedPrefs.edit().putBoolean("auto_ocr", it).apply()
                    })
                }
            )
            ListItem(
                headlineContent = { Text("OCR Language") },
                supportingContent = { Text(if (ocrLanguage == "ur") "Urdu (اردو)" else "English") },
                modifier = Modifier.clickable { showLanguageDialog = true }
            )
            HorizontalDivider()

            if (showLanguageDialog) {
                AlertDialog(
                    onDismissRequest = { showLanguageDialog = false },
                    title = { Text("Select OCR Language") },
                    text = {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        ocrLanguage = "en"
                                        sharedPrefs.edit().putString("ocr_language", "en").apply()
                                        showLanguageDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                RadioButton(selected = ocrLanguage == "en", onClick = {
                                    ocrLanguage = "en"
                                    sharedPrefs.edit().putString("ocr_language", "en").apply()
                                    showLanguageDialog = false
                                })
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("English")
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        ocrLanguage = "ur"
                                        sharedPrefs.edit().putString("ocr_language", "ur").apply()
                                        showLanguageDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                RadioButton(selected = ocrLanguage == "ur", onClick = {
                                    ocrLanguage = "ur"
                                    sharedPrefs.edit().putString("ocr_language", "ur").apply()
                                    showLanguageDialog = false
                                })
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Urdu (اردو)")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLanguageDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Text("Scanner Settings", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 8.dp))
            ListItem(headlineContent = { Text("Scan Quality") }, supportingContent = { Text("High") })
            ListItem(headlineContent = { Text("Auto Crop") }, trailingContent = { Switch(checked = true, onCheckedChange = {}) })
            HorizontalDivider()

            Text("Storage", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 8.dp))
            ListItem(headlineContent = { Text("Clear Cache") })
            HorizontalDivider()

            Text("About", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 8.dp))
            ListItem(
                headlineContent = { Text("ScanVerse") },
                supportingContent = { Text("Version 1.0") },
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
