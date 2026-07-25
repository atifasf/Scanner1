package com.example.ui.ai

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object AIPdfPasswordProtection {

    /**
     * Secures a PDF file with AES-128 encryption wrapper derived from user password.
     */
    fun encryptPdf(inputFile: File, outputFile: File, password: String): Boolean {
        if (password.isBlank()) return false
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, 0, 16, "AES")

            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val inputData = inputFile.readBytes()
            val encryptedData = cipher.doFinal(inputData)

            // Write custom encrypted header tag
            FileOutputStream(outputFile).use { out ->
                out.write("SCANVERSE_ENCRYPTED_PDF_V1\n".toByteArray(Charsets.UTF_8))
                out.write(encryptedData)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Decrypts a password protected PDF for viewing/sharing if the password matches.
     */
    fun decryptPdf(inputFile: File, outputFile: File, password: String): Boolean {
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, 0, 16, "AES")

            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)

            val bytes = inputFile.readBytes()
            val headerStr = "SCANVERSE_ENCRYPTED_PDF_V1\n"
            val headerBytes = headerStr.toByteArray(Charsets.UTF_8)

            if (bytes.size <= headerBytes.size) return false

            val payload = bytes.copyOfRange(headerBytes.size, bytes.size)
            val decryptedData = cipher.doFinal(payload)

            FileOutputStream(outputFile).use { out ->
                out.write(decryptedData)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun isEncrypted(file: File): Boolean {
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
}
