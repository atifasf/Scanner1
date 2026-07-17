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
import com.example.ui.screens.AdminPanelScreen

import android.content.Context

import androidx.compose.ui.platform.LocalContext
import android.content.SharedPreferences
import com.google.android.gms.ads.MobileAds

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Pre-create WebView Code Cache directories to prevent Chromium readdir/opendir errors
    createWebViewCacheDirs()

    // Also recreate them after a delay of 1, 2, and 5 seconds to ensure they persist after WebView/Chromium initialization clears its caches
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    handler.postDelayed({ createWebViewCacheDirs() }, 1000)
    handler.postDelayed({ createWebViewCacheDirs() }, 2000)
    handler.postDelayed({ createWebViewCacheDirs() }, 5000)

    MobileAds.initialize(this) {}
    enableEdgeToEdge()
    
    setContent {
            val context = LocalContext.current
            val sharedPrefs = remember<SharedPreferences> { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
            var isDarkTheme by remember { mutableStateOf(sharedPrefs.getBoolean("dark_theme", false)) }
            
            androidx.compose.runtime.DisposableEffect(sharedPrefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                    if (key == "dark_theme") {
                        isDarkTheme = prefs.getBoolean("dark_theme", false)
                    }
                }
                sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

      MyApplicationTheme(darkTheme = isDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val navController = rememberNavController()
            val viewModel: DocumentViewModel = viewModel()

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
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToAdmin = { navController.navigate("admin") }
                    )
                }
                composable("admin") {
                    AdminPanelScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
      }
    }
  }

  private fun createWebViewCacheDirs() {
    try {
        val jsDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
        if (!jsDir.exists()) {
            jsDir.mkdirs()
        }
        val jsPlaceholder = java.io.File(jsDir, ".placeholder")
        if (!jsPlaceholder.exists()) {
            jsPlaceholder.createNewFile()
        }
        
        val wasmDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
        if (!wasmDir.exists()) {
            wasmDir.mkdirs()
        }
        val wasmPlaceholder = java.io.File(wasmDir, ".placeholder")
        if (!wasmPlaceholder.exists()) {
            wasmPlaceholder.createNewFile()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
  }
}

