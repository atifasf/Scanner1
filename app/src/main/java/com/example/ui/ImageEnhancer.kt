package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ImageEnhancer {
    fun enhanceBitmap(original: Bitmap): Bitmap {
        val enhanced = Bitmap.createBitmap(original.width, original.height, original.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        val paint = Paint()

        // Create a ColorMatrix for contrast and brightness
        val colorMatrix = ColorMatrix()
        
        // Increase contrast by 1.2x (20% more contrast)
        val contrast = 1.2f
        // Increase brightness slightly
        val brightness = 10f
        
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        
        // Increase saturation by 1.1x
        val saturationMatrix = ColorMatrix()
        saturationMatrix.setSaturation(1.1f)
        
        colorMatrix.postConcat(contrastMatrix)
        colorMatrix.postConcat(saturationMatrix)
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        
        canvas.drawBitmap(original, 0f, 0f, paint)
        return enhanced
    }
}
