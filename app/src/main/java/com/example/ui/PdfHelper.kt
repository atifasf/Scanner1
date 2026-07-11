package com.example.ui

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File

object PdfHelper {
    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            PDFBoxResourceLoader.init(context)
            isInitialized = true
        }
    }

    fun encryptPdf(context: Context, pdfPath: String, password: String): Boolean {
        init(context)
        return try {
            val file = File(pdfPath)
            if (!file.exists()) return false

            val document = PDDocument.load(file)
            val accessPermission = AccessPermission()
            val spp = StandardProtectionPolicy(password, password, accessPermission)
            spp.encryptionKeyLength = 128
            spp.permissions = accessPermission
            
            document.protect(spp)
            document.save(file)
            document.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
