package com.example.ui.ai

import android.content.Context
import android.graphics.Bitmap
import com.example.ui.AutoDeskewEnhancer
import com.example.ui.EnhancementMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MagicScanResult(
    val originalBitmap: Bitmap,
    val enhancedBitmap: Bitmap,
    val isBlurry: Boolean,
    val blurScore: Float,
    val blurMessage: String,
    val suggestedFileName: String
)

object AIMagicScan {

    /**
     * Executes single-tap AI Magic Scan pipeline:
     * Edge detection -> Perspective Warp -> Auto Deskew -> Shadow Removal -> Finger Removal -> Whitening & Text Sharpening -> Blur Check -> Smart Naming
     */
    suspend fun processMagicScan(
        context: Context,
        inputBitmap: Bitmap,
        ocrText: String? = null
    ): MagicScanResult = withContext(Dispatchers.IO) {

        // Step 1: Detect corners & Perspective Warp & Auto Deskew & CamScanner Whitening
        val warpedAndDeskewed = AutoDeskewEnhancer.autoProcessDocument(
            context = context,
            original = inputBitmap,
            mode = EnhancementMode.MAGIC_COLOR
        )

        // Step 2: AI Shadow Removal
        val shadowRemoved = AIShadowRemover.removeShadows(warpedAndDeskewed)
        if (shadowRemoved != warpedAndDeskewed && warpedAndDeskewed != inputBitmap) {
            warpedAndDeskewed.recycle()
        }

        // Step 3: AI Finger Removal
        val fingerRemoved = AIFingerRemover.removeFingers(shadowRemoved)
        if (fingerRemoved != shadowRemoved && shadowRemoved != inputBitmap) {
            shadowRemoved.recycle()
        }

        // Step 4: AI Blur Detection
        val blurCheck = AIBlurDetector.checkBlur(fingerRemoved)

        // Step 5: Smart Auto File Naming
        val smartName = AISmartNaming.generateSmartFileName(ocrText)

        return@withContext MagicScanResult(
            originalBitmap = inputBitmap,
            enhancedBitmap = fingerRemoved,
            isBlurry = blurCheck.isBlurry,
            blurScore = blurCheck.score,
            blurMessage = blurCheck.warningMessage,
            suggestedFileName = smartName
        )
    }
}
