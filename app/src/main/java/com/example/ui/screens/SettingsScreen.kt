package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
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
        ) {
            ListItem(
                headlineContent = { Text("Biometric Authentication") },
                supportingContent = { Text("Use fingerprint or face to unlock app") },
                trailingContent = {
                    var checked by remember { mutableStateOf(true) }
                    Switch(checked = checked, onCheckedChange = { checked = it })
                }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Default Export Format") },
                supportingContent = { Text("PDF") },
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Divider()
            ListItem(
                headlineContent = { Text("About") },
                supportingContent = { Text("ScanVerse Version 1.0") },
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
