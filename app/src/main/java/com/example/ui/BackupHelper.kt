package com.example.ui

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BackupHelper {
    fun createBackupZip(context: Context, uri: Uri): Boolean {
        return try {
            val dbFile = context.getDatabasePath("document_database")
            val pfd = context.contentResolver.openFileDescriptor(uri, "w") ?: return false
            val outStream = FileOutputStream(pfd.fileDescriptor)
            val zipOut = ZipOutputStream(outStream)

            if (dbFile.exists()) {
                addFileToZip(dbFile, "document_database.db", zipOut)
            }
            
            // Also add shm and wal if they exist (Room database temporary files)
            val dbFileShm = File(dbFile.path + "-shm")
            if (dbFileShm.exists()) addFileToZip(dbFileShm, "document_database.db-shm", zipOut)
            
            val dbFileWal = File(dbFile.path + "-wal")
            if (dbFileWal.exists()) addFileToZip(dbFileWal, "document_database.db-wal", zipOut)

            // We could also backup the entire files dir (images)
            val filesDir = context.filesDir
            backupDirectory(filesDir, filesDir.path, zipOut)

            zipOut.close()
            outStream.close()
            pfd.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun backupDirectory(dir: File, basePath: String, zipOut: ZipOutputStream) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                backupDirectory(file, basePath, zipOut)
            } else {
                // Don't backup cache or profile stuff if possible, just keep it simple
                val entryName = "files/" + file.path.substring(basePath.length + 1)
                addFileToZip(file, entryName, zipOut)
            }
        }
    }

    private fun addFileToZip(file: File, entryName: String, zipOut: ZipOutputStream) {
        val fis = FileInputStream(file)
        val zipEntry = ZipEntry(entryName)
        zipOut.putNextEntry(zipEntry)
        
        val bytes = ByteArray(1024)
        var length: Int
        while (fis.read(bytes).also { length = it } >= 0) {
            zipOut.write(bytes, 0, length)
        }
        
        zipOut.closeEntry()
        fis.close()
    }
}
