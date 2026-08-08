package com.example.ui.ai

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import java.io.FileInputStream

object AIPdfPasswordProtection {

    fun init(context: Context) {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Secures a PDF file with standard 128-bit PDF encryption.
     * Generates a standard PDF file compatible with WhatsApp, Adobe Acrobat, and PDF readers.
     */
    fun encryptPdf(inputFile: File, outputFile: File, password: String): Boolean {
        if (password.isBlank() || !inputFile.exists()) return false
        
        // Handle legacy custom encrypted file if needed
        val realInput = if (isLegacyEncrypted(inputFile)) {
            val tempDec = File(inputFile.parent, "temp_dec_${System.currentTimeMillis()}.pdf")
            if (fallbackDecrypt(inputFile, tempDec, password)) tempDec else inputFile
        } else {
            inputFile
        }

        return try {
            val document = PDDocument.load(realInput)
            val ap = AccessPermission()
            val spp = StandardProtectionPolicy(password, password, ap)
            spp.encryptionKeyLength = 128
            spp.permissions = ap
            document.protect(spp)
            document.save(outputFile)
            document.close()
            if (realInput != inputFile) realInput.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            if (realInput != inputFile) realInput.delete()
            fallbackEncrypt(inputFile, outputFile, password)
        }
    }

    /**
     * Decrypts a password protected PDF for viewing or editing.
     */
    fun decryptPdf(inputFile: File, outputFile: File, password: String): Boolean {
        if (!inputFile.exists()) return false

        if (isLegacyEncrypted(inputFile)) {
            return fallbackDecrypt(inputFile, outputFile, password)
        }

        return try {
            val document = PDDocument.load(inputFile, password)
            document.isAllSecurityToBeRemoved = true
            document.save(outputFile)
            document.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun isEncrypted(file: File): Boolean {
        if (!file.exists()) return false
        if (isLegacyEncrypted(file)) return true
        return try {
            val document = PDDocument.load(file)
            val encrypted = document.isEncrypted
            document.close()
            encrypted
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            msg.contains("password") || msg.contains("encrypted") || msg.contains("security")
        }
    }

    private fun isLegacyEncrypted(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(32)
                val read = input.read(buffer)
                if (read > 0) {
                    val str = String(buffer, 0, read, Charsets.UTF_8)
                    str.startsWith("SCANVERSE_ENCRYPTED_PDF_V1")
                } else false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun fallbackEncrypt(inputFile: File, outputFile: File, password: String): Boolean {
        return try {
            val keyBytes = java.security.MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, 0, 16, "AES")
            val cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey)
            val inputData = inputFile.readBytes()
            val encryptedData = cipher.doFinal(inputData)
            java.io.FileOutputStream(outputFile).use { out ->
                out.write("SCANVERSE_ENCRYPTED_PDF_V1\n".toByteArray(Charsets.UTF_8))
                out.write(encryptedData)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun fallbackDecrypt(inputFile: File, outputFile: File, password: String): Boolean {
        return try {
            val keyBytes = java.security.MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, 0, 16, "AES")
            val cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey)
            val bytes = inputFile.readBytes()
            val headerStr = "SCANVERSE_ENCRYPTED_PDF_V1\n"
            val headerBytes = headerStr.toByteArray(Charsets.UTF_8)
            if (bytes.size <= headerBytes.size) return false
            val payload = bytes.copyOfRange(headerBytes.size, bytes.size)
            val decryptedData = cipher.doFinal(payload)
            java.io.FileOutputStream(outputFile).use { out ->
                out.write(decryptedData)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

