package com.example.ui

import android.content.Context
import android.media.AudioManager
import android.media.MediaActionSound
import android.media.ToneGenerator

object SoundHelper {
    private var mediaActionSound: MediaActionSound? = null
    
    // Initialize lazily
    private fun getMediaActionSound(): MediaActionSound {
        if (mediaActionSound == null) {
            mediaActionSound = MediaActionSound()
            mediaActionSound?.load(MediaActionSound.SHUTTER_CLICK)
        }
        return mediaActionSound!!
    }

    fun playShutterSound(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("capture_scan_sounds", true)) {
            getMediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
        }
    }

    fun playScanSound(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("capture_scan_sounds", true)) {
            try {
                // Subtle prompt tone suitable for scanning feedback
                val toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 60)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                // Need a thread or Coroutine to release ToneGenerator correctly 
                // but doing it synchronously right after startTone is bad as it cuts off.
                // We just let it GC or we can start a short thread.
                Thread {
                    Thread.sleep(200)
                    toneGen.release()
                }.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
