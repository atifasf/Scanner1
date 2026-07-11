package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.DocumentViewModel
import com.example.ui.SecurityHelper
import com.example.ui.screens.DocumentDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment

import com.example.ui.screens.SettingsScreen

import android.content.Context

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    setContent {
      MyApplicationTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val sharedPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val isBiometricEnabled = sharedPrefs.getBoolean("biometric_enabled", true)
            var isAuthenticated by remember { mutableStateOf(!isBiometricEnabled) }
            val navController = rememberNavController()
            val viewModel: DocumentViewModel = viewModel()

            if (!isAuthenticated) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    SecurityHelper.authenticate(
                        activity = this@MainActivity,
                        onSuccess = { isAuthenticated = true },
                        onError = { /* Handle error gracefully or retry */ }
                    )
                }
            } else {
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToDetail = { id ->
                                navController.navigate("detail/$id")
                            },
                            onNavigateToSettings = {
                                navController.navigate("settings")
                            }
                        )
                    }
                    composable("detail/{documentId}") { backStackEntry ->
                        val documentId = backStackEntry.arguments?.getString("documentId") ?: return@composable
                        DocumentDetailScreen(
                            documentId = documentId,
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
      }
    }
  }
}

