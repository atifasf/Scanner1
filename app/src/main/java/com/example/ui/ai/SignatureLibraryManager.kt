package com.example.ui.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class SavedSignature(
    val id: String,
    val name: String,
    val imagePath: String,
    val dateAdded: Long
)

object SignatureLibraryManager {
    private const val PREF_NAME = "my_signatures_pref"
    private const val KEY_SIGNATURES = "signatures_json"

    fun getSavedSignatures(context: Context): List<SavedSignature> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SIGNATURES, null) ?: return emptyList()
        val list = mutableListOf<SavedSignature>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val path = obj.getString("imagePath")
                val file = File(path)
                if (file.exists()) {
                    list.add(
                        SavedSignature(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            imagePath = path,
                            dateAdded = obj.getLong("dateAdded")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.dateAdded }
    }

    fun saveSignature(context: Context, bitmap: Bitmap, name: String = ""): SavedSignature {
        val sigDir = File(context.filesDir, "signatures")
        if (!sigDir.exists()) sigDir.mkdirs()

        val id = UUID.randomUUID().toString()
        val sigFile = File(sigDir, "sig_$id.png")
        FileOutputStream(sigFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val existing = getSavedSignatures(context).toMutableList()
        val sigName = if (name.isBlank()) "Signature ${existing.size + 1}" else name
        val newSig = SavedSignature(
            id = id,
            name = sigName,
            imagePath = sigFile.absolutePath,
            dateAdded = System.currentTimeMillis()
        )
        existing.add(0, newSig)
        saveToPrefs(context, existing)
        return newSig
    }

    fun renameSignature(context: Context, id: String, newName: String) {
        val existing = getSavedSignatures(context).map {
            if (it.id == id) it.copy(name = newName) else it
        }
        saveToPrefs(context, existing)
    }

    fun deleteSignature(context: Context, id: String) {
        val existing = getSavedSignatures(context).toMutableList()
        val target = existing.find { it.id == id }
        if (target != null) {
            try {
                File(target.imagePath).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            existing.remove(target)
            saveToPrefs(context, existing)
        }
    }

    private fun saveToPrefs(context: Context, list: List<SavedSignature>) {
        val array = JSONArray()
        for (sig in list) {
            val obj = JSONObject().apply {
                put("id", sig.id)
                put("name", sig.name)
                put("imagePath", sig.imagePath)
                put("dateAdded", sig.dateAdded)
            }
            array.put(obj)
        }
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SIGNATURES, array.toString()).apply()
    }
}
