package com.example.ui

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import android.content.Context
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_BASE
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

object ScannerHelper {

    /**
     * Restores all Camera Capturing and Scanner settings to Version 5 defaults.
     */
    fun restoreVersion5Settings(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("capture_scan_sounds", true)
            .putBoolean("auto_crop", true)
            .putString("scan_quality", "High")
            .putString("scanner_mode", "FULL")
            .putInt("scanner_page_limit", 50)
            .putBoolean("scanner_gallery_import", true)
            .putBoolean("auto_deskew_magic", true)
            .putBoolean("v5_settings_restored", true)
            .apply()
    }

    fun getScannerOptions(context: Context? = null): GmsDocumentScannerOptions {
        var galleryAllowed = true
        var pageLimit = 50
        var scannerMode = SCANNER_MODE_FULL

        if (context != null) {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            galleryAllowed = prefs.getBoolean("scanner_gallery_import", true)
            pageLimit = prefs.getInt("scanner_page_limit", 50)
            val modeStr = prefs.getString("scanner_mode", "FULL") ?: "FULL"
            scannerMode = when (modeStr) {
                "BASE_WITH_FILTER" -> SCANNER_MODE_BASE_WITH_FILTER
                "BASE" -> SCANNER_MODE_BASE
                else -> SCANNER_MODE_FULL
            }
        }

        return GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(galleryAllowed)
            .setPageLimit(pageLimit)
            .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
            .setScannerMode(scannerMode)
            .build()
    }

    fun startScan(
        activity: Activity,
        launcher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>
    ) {
        val scanner = GmsDocumentScanning.getClient(getScannerOptions(activity))
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(activity, "Failed to start scanner: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    fun handleScanResult(
        result: ActivityResult,
        onSuccess: (List<Uri>, Uri?) -> Unit
    ) {
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.let {
                val imageUris = it.pages?.map { page -> page.imageUri } ?: emptyList()
                val pdfUri = it.pdf?.uri
                onSuccess(imageUris, pdfUri)
            }
        }
    }
}
