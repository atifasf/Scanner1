package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object OCRHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun extractText(
        context: Context,
        uri: Uri,
        languageCode: String = "en",
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (languageCode == "ur") {
            // For Urdu/Arabic script, we use Gemini API for accurate, high-quality multimodal OCR
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val base64Image = uriToBase64(context, uri)
                    if (base64Image == null) {
                        launch(Dispatchers.Main) {
                            onError(Exception("Failed to load image for OCR."))
                        }
                        return@launch
                    }

                    val apiKey = BuildConfig.GEMINI_API_KEY
                    if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                        launch(Dispatchers.Main) {
                            onError(Exception("Gemini API Key is missing. Please add GEMINI_API_KEY to Secrets in AI Studio."))
                        }
                        return@launch
                    }

                    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

                    // Create JSON Request
                    val requestJson = JSONObject()
                    val contentsArray = org.json.JSONArray()
                    val contentObject = JSONObject()
                    val partsArray = org.json.JSONArray()

                    val textPart = JSONObject()
                    textPart.put("text", "Extract and output only the Urdu text from this image. Do not translate. Output only the exact words found in the image. No commentary, no explanations, no preamble, and no markdown formatting.")

                    val imagePart = JSONObject()
                    val inlineData = JSONObject()
                    inlineData.put("mimeType", "image/jpeg")
                    inlineData.put("data", base64Image)
                    imagePart.put("inlineData", inlineData)

                    partsArray.put(textPart)
                    partsArray.put(imagePart)
                    contentObject.put("parts", partsArray)
                    contentsArray.put(contentObject)
                    requestJson.put("contents", contentsArray)

                    val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            launch(Dispatchers.Main) {
                                onError(IOException("Unexpected response code: ${response.code}"))
                            }
                            return@use
                        }

                        val responseBodyString = response.body?.string()
                        if (responseBodyString == null) {
                            launch(Dispatchers.Main) {
                                onError(IOException("Empty response from Gemini API"))
                            }
                            return@use
                        }

                        val jsonResponse = JSONObject(responseBodyString)
                        val extractedText = jsonResponse.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")

                        launch(Dispatchers.Main) {
                            onSuccess(extractedText.trim())
                        }
                    }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) {
                        onError(e)
                    }
                }
            }
        } else {
            // For English/Latin, we use Google on-device ML Kit Text Recognition
            try {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        onSuccess(visionText.text)
                    }
                    .addOnFailureListener { e ->
                        onError(e)
                    }
            } catch (e: IOException) {
                onError(e)
            }
        }
    }

    fun extractTableAsCsv(
        context: Context,
        uri: Uri,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val base64Image = uriToBase64(context, uri)
                if (base64Image == null) {
                    launch(Dispatchers.Main) { onError(Exception("Failed to load image.")) }
                    return@launch
                }
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    launch(Dispatchers.Main) { onError(Exception("Gemini API Key missing.")) }
                    return@launch
                }
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
                val requestJson = JSONObject()
                val contentsArray = org.json.JSONArray()
                val contentObject = JSONObject()
                val partsArray = org.json.JSONArray()
                val textPart = JSONObject()
                textPart.put("text", "Extract the table from this image and output it strictly in CSV format using the pipe character `|` as the delimiter. Do not include markdown code blocks, do not include any other text. Keep columns aligned with pipes.")
                val imagePart = JSONObject()
                val inlineData = JSONObject()
                inlineData.put("mimeType", "image/jpeg")
                inlineData.put("data", base64Image)
                imagePart.put("inlineData", inlineData)
                partsArray.put(textPart)
                partsArray.put(imagePart)
                contentObject.put("parts", partsArray)
                contentsArray.put(contentObject)
                requestJson.put("contents", contentsArray)

                val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).post(requestBody).build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        launch(Dispatchers.Main) { onError(IOException("Error: ${response.code}")) }
                        return@use
                    }
                    val jsonResponse = JSONObject(response.body?.string() ?: "")
                    val extractedText = jsonResponse.getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text")
                    launch(Dispatchers.Main) { onSuccess(extractedText.trim().removePrefix("```csv\n").removeSuffix("\n```").trim()) }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onError(e) }
            }
        }
    }

    private fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            val outputStream = ByteArrayOutputStream()
            bitmap?.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
