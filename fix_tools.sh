#!/bin/bash
cat << 'INNER_EOF' > app/src/main/java/com/example/ui/screens/ToolsTab.kt
package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.BackupHelper
import com.example.ui.DocumentViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsTab(viewModel: DocumentViewModel, padding: PaddingValues, context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val success = BackupHelper.createBackupZip(context, uri)
                val msg = if (success) "Backup created successfully!" else "Failed to create backup."
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Scanner Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        // Backup Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Backup & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = {
                        createBackupLauncher.launch("DocumentScannerBackup_${System.currentTimeMillis()}.zip")
                    }) {
                        Icon(Icons.Default.Archive, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Backup ZIP")
                    }
                }
            }
        }
    }
}
INNER_EOF
