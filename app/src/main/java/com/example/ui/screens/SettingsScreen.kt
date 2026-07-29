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

data class OcrLangOption(val code: String, val name: String)

val topLanguages = listOf(
    OcrLangOption("en", "English"),
    OcrLangOption("ur", "Urdu (اردو)"),
    OcrLangOption("ar", "Arabic (العربية)"),
    OcrLangOption("es", "Spanish (Español)"),
    OcrLangOption("fr", "French (Français)"),
    OcrLangOption("de", "German (Deutsch)"),
    OcrLangOption("zh", "Chinese (中文)"),
    OcrLangOption("hi", "Hindi (हिन्दी)"),
    OcrLangOption("ru", "Russian (Русский)"),
    OcrLangOption("ja", "Japanese (日本語)")
)

private fun calculateCacheSize(context: Context): Long {
    var size = 0L
    fun getDirSize(dir: java.io.File?) {
        if (dir == null || !dir.exists()) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                getDirSize(f)
            } else {
                size += f.length()
            }
        }
    }
    getDirSize(context.cacheDir)
    getDirSize(context.externalCacheDir)
    return size
}

private fun formatSize(bytes: Long): String {
    val mb = bytes.toDouble() / (1024 * 1024)
    return String.format("%.2f MB", mb)
}

private fun performClearCache(context: Context): Long {
    var freed = 0L
    fun deleteDir(dir: java.io.File?) {
        if (dir == null || !dir.exists()) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                deleteDir(f)
                f.delete()
            } else {
                freed += f.length()
                f.delete()
            }
        }
    }
    deleteDir(context.cacheDir)
    deleteDir(context.externalCacheDir)
    try {
        coil.Coil.imageLoader(context).diskCache?.clear()
        coil.Coil.imageLoader(context).memoryCache?.clear()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return freed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAdmin: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    
    var isDarkTheme by remember { mutableStateOf(sharedPrefs.getBoolean("dark_theme", true)) }
    var autoOcr by remember { mutableStateOf(sharedPrefs.getBoolean("auto_ocr", false)) }
    var ocrLanguage by remember { mutableStateOf(sharedPrefs.getString("ocr_language", "en") ?: "en") }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var cacheSizeText by remember { mutableStateOf(formatSize(calculateCacheSize(context))) }

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
                supportingContent = { Text(if (isDarkTheme) "Dark theme enabled by default" else "Light theme enabled") },
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
                supportingContent = { Text(topLanguages.find { it.code == ocrLanguage }?.name ?: "English") },
                modifier = Modifier.clickable { showLanguageDialog = true }
            )
            HorizontalDivider()

            if (showLanguageDialog) {
                AlertDialog(
                    onDismissRequest = { showLanguageDialog = false },
                    title = { Text("Select OCR Language") },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            topLanguages.forEach { lang ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            ocrLanguage = lang.code
                                            sharedPrefs.edit().putString("ocr_language", lang.code).apply()
                                            showLanguageDialog = false
                                        }
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = ocrLanguage == lang.code,
                                        onClick = {
                                            ocrLanguage = lang.code
                                            sharedPrefs.edit().putString("ocr_language", lang.code).apply()
                                            showLanguageDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(lang.name, style = MaterialTheme.typography.bodyMedium)
                                }
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
            ListItem(
                headlineContent = { Text("Clear Cache") },
                supportingContent = { Text("Cache size: $cacheSizeText (Tap to clear)") },
                modifier = Modifier.clickable {
                    val freedBytes = performClearCache(context)
                    val freedText = formatSize(freedBytes)
                    cacheSizeText = formatSize(calculateCacheSize(context))
                    Toast.makeText(context, "Cache cleared successfully ($freedText freed)", Toast.LENGTH_SHORT).show()
                }
            )
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
