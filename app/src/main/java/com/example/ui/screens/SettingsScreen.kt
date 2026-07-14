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

import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.BackupHelper
import com.example.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAdmin: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    
    var isDarkTheme by remember { mutableStateOf(sharedPrefs.getBoolean("dark_theme", false)) }
    var autoOcr by remember { mutableStateOf(sharedPrefs.getBoolean("auto_ocr", false)) }
    var ocrLanguage by remember { mutableStateOf(sharedPrefs.getString("ocr_language", "en") ?: "en") }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val success = BackupHelper.createBackupZip(context, uri)
                if (success) {
                    Toast.makeText(context, "Backup saved to Google Drive / Local", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Backup failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
            Text("Backup", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 8.dp))
            ListItem(
                headlineContent = { Text("Backup to Storage") },
                supportingContent = { Text("Export database securely") },
                modifier = Modifier.clickable {
                    exportLauncher.launch("ScanVerse_Backup.zip")
                }
            )
            HorizontalDivider()

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
            
            Spacer(modifier = Modifier.weight(1f, fill = false))
            com.example.ui.components.BannerAd()
        }
    }
}
