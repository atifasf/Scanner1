package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DocumentEntity
import com.example.data.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

import com.example.data.FolderEntity
import com.example.data.FolderDao

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.util.Units
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.graphics.pdf.PdfRenderer

class DocumentViewModel(application: Application) : AndroidViewModel(application) {
    enum class OutputFormat { PDF, JPEG, WORD }

    var ocrImageUri by mutableStateOf<Uri?>(null)
    var ocrExtractedText by mutableStateOf("")
    var ocrIsTableSelected by mutableStateOf(false)

    val ocrProgress = MutableStateFlow<String?>(null)
    private val repository: DocumentRepository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val allDocuments: StateFlow<List<DocumentEntity>>
    val favoriteDocuments: StateFlow<List<DocumentEntity>>
    val trashDocuments: StateFlow<List<DocumentEntity>>
    val allFolders: StateFlow<List<FolderEntity>>

    init {
        val db = AppDatabase.getDatabase(application)
        repository = DocumentRepository(db.documentDao(), db.folderDao())

        allDocuments = repository.allDocuments.combine(_searchQuery) { docs, query ->
            if (query.isBlank()) docs else docs.filter { it.name.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        favoriteDocuments = repository.favoriteDocuments.combine(_searchQuery) { docs, query ->
            if (query.isBlank()) docs else docs.filter { it.name.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        trashDocuments = repository.trashDocuments.combine(_searchQuery) { docs, query ->
            if (query.isBlank()) docs else docs.filter { it.name.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
        allFolders = repository.allFolders.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun getDocumentById(id: String): DocumentEntity? {
        return repository.getDocumentById(id)
    }

    private fun isTextDocument(bitmap: Bitmap): Boolean {
        var colorfulPixelCount = 0
        val sampleStep = 30
        var totalSamples = 0
        for (y in 0 until bitmap.height step sampleStep) {
            for (x in 0 until bitmap.width step sampleStep) {
                val color = bitmap.getPixel(x, y)
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val diff = max - min
                if (max > 0 && (diff.toFloat() / max) > 0.2f) {
                    colorfulPixelCount++
                }
                totalSamples++
            }
        }
        val colorfulRatio = if (totalSamples > 0) colorfulPixelCount.toFloat() / totalSamples else 0f
        return colorfulRatio < 0.15f
    }

    private fun resizeBitmapIfNeeded(original: Bitmap, maxDimension: Int): Bitmap {
        val width = original.width
        val height = original.height
        if (width <= maxDimension && height <= maxDimension) return original
        
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
    }

    fun saveScannedDocument(imageUris: List<Uri>, pdfUri: Uri?, folderId: String? = null) {
        saveScannedDocumentWithFormat(
            imageUris = imageUris,
            format = OutputFormat.PDF,
            isSearchablePdf = false,
            customName = null,
            folderId = folderId,
            onComplete = {}
        )
    }

    fun saveScannedDocumentWithFormat(
        imageUris: List<Uri>,
        format: OutputFormat,
        isSearchablePdf: Boolean,
        customName: String? = null,
        folderId: String? = null,
        isIdCardGrid: Boolean = false,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            ocrProgress.value = "Optimizing images..."
            val app = getApplication<Application>()
            val id = UUID.randomUUID().toString()
            val savedImages = mutableListOf<String>()
            
            // Step 1: Intelligent resizing & compression
            imageUris.forEachIndexed { index, uri ->
                val file = File(app.filesDir, "img_${id}_$index.jpg")
                try {
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    var inputStream = app.contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream, null, options)
                    inputStream?.close()

                    // Calculate inSampleSize
                    val maxDim = 1600
                    var inSampleSize = 1
                    if (options.outHeight > maxDim || options.outWidth > maxDim) {
                        val halfHeight = options.outHeight / 2
                        val halfWidth = options.outWidth / 2
                        while ((halfHeight / inSampleSize) >= maxDim && (halfWidth / inSampleSize) >= maxDim) {
                            inSampleSize *= 2
                        }
                    }

                    options.inJustDecodeBounds = false
                    options.inSampleSize = inSampleSize
                    
                    inputStream = app.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream, null, options)
                    inputStream?.close()

                    if (originalBitmap != null) {
                        val isText = isTextDocument(originalBitmap)
                        val quality = if (isText) 65 else 80
                        
                        // Resize if still larger than maxDim to be safe
                        val resized = resizeBitmapIfNeeded(originalBitmap, maxDim)
                        
                        // Enhance
                        val enhanced = com.example.ui.ImageEnhancer.enhanceBitmap(resized)
                        
                        file.outputStream().use { output ->
                            enhanced.compress(Bitmap.CompressFormat.JPEG, quality, output)
                        }
                        
                        // Recycle to free memory
                        if (resized != originalBitmap) resized.recycle()
                        originalBitmap.recycle()
                        enhanced.recycle()
                    } else {
                        app.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                savedImages.add(file.absolutePath)
            }
            
            var savedDocPath: String? = null
            var finalOcrText: String? = null

            when (format) {
                OutputFormat.JPEG -> {
                    // Saves only JPEG images.
                    savedDocPath = null
                }
                OutputFormat.PDF -> {
                    ocrProgress.value = "Generating PDF..."
                    val pdfFile = File(app.filesDir, "pdf_$id.pdf")
                    try {
                        val pdfDocument = PdfDocument()
                        
                        if (isIdCardGrid) {
                            val pageWidth = 595
                            val pageHeight = 842
                            
                            val cardWidth = 185f
                            val cardHeight = 117f
                            val hGap = 10f
                            val vGap = 15f
                            
                            val totalGridWidth = 3 * cardWidth + 2 * hGap
                            val leftMargin = (pageWidth - totalGridWidth) / 2f
                            
                            val totalGridHeight = 3 * cardHeight + 2 * vGap
                            val topMargin = (pageHeight - totalGridHeight) / 2f
                            
                            savedImages.forEachIndexed { index, imagePath ->
                                val bitmap = BitmapFactory.decodeFile(imagePath)
                                if (bitmap != null) {
                                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                                    val page = pdfDocument.startPage(pageInfo)
                                    val canvas = page.canvas
                                    
                                    // Fill background white
                                    canvas.drawColor(Color.WHITE)
                                    
                                    val borderPaint = Paint().apply {
                                        color = Color.LTGRAY
                                        style = Paint.Style.STROKE
                                        strokeWidth = 0.5f
                                    }
                                    
                                    // Draw 3x3 grid
                                    for (row in 0..2) {
                                        for (col in 0..2) {
                                            val left = leftMargin + col * (cardWidth + hGap)
                                            val top = topMargin + row * (cardHeight + vGap)
                                            val rect = android.graphics.RectF(left, top, left + cardWidth, top + cardHeight)
                                            
                                            canvas.drawBitmap(bitmap, null, rect, null)
                                            canvas.drawRect(rect, borderPaint)
                                        }
                                    }
                                    
                                    pdfDocument.finishPage(page)
                                    bitmap.recycle()
                                }
                            }
                        } else {
                            savedImages.forEachIndexed { index, imagePath ->
                                if (isSearchablePdf) {
                                    ocrProgress.value = "Performing OCR (Page ${index + 1} of ${savedImages.size})..."
                                }
                                
                                val bitmap = BitmapFactory.decodeFile(imagePath)
                                if (bitmap != null) {
                                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                                    val page = pdfDocument.startPage(pageInfo)
                                    val canvas = page.canvas
                                    
                                    // Draw image on canvas
                                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                                    
                                    if (isSearchablePdf) {
                                        val uri = Uri.fromFile(File(imagePath))
                                        val inputImage = InputImage.fromFilePath(app, uri)
                                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                                        
                                        try {
                                            val visionText = Tasks.await(recognizer.process(inputImage))
                                            if (visionText != null) {
                                                if (finalOcrText == null) finalOcrText = ""
                                                finalOcrText += visionText.text + "\n"
                                                
                                                val paint = Paint().apply {
                                                    color = Color.TRANSPARENT
                                                    textSize = 12f
                                                }
                                                
                                                visionText.textBlocks.forEach { block ->
                                                    block.lines.forEach { line ->
                                                        val bbox = line.boundingBox
                                                        if (bbox != null) {
                                                            paint.textSize = bbox.height().toFloat() * 0.8f
                                                            canvas.drawText(line.text, bbox.left.toFloat(), bbox.bottom.toFloat(), paint)
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                    
                                    pdfDocument.finishPage(page)
                                    bitmap.recycle()
                                }
                            }
                        }
                        
                        pdfDocument.writeTo(pdfFile.outputStream())
                        pdfDocument.close()
                        savedDocPath = pdfFile.absolutePath
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                OutputFormat.WORD -> {
                    ocrProgress.value = "Generating Word Document..."
                    try {
                        val wordFile = File(app.filesDir, "word_$id.docx")
                        val doc = XWPFDocument()
                        val extractedTexts = mutableListOf<String>()
                        
                        val sharedPrefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        val ocrLanguage = sharedPrefs.getString("ocr_language", "en") ?: "en"
                        
                        savedImages.forEachIndexed { index, imagePath ->
                            ocrProgress.value = "Performing OCR (Page ${index + 1} of ${savedImages.size})...."
                            val file = File(imagePath)
                            val uri = Uri.fromFile(file)
                            
                            val inputImage = InputImage.fromFilePath(app, uri)
                            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            
                            val visionText = if (ocrLanguage == "ur") {
                                null
                            } else {
                                try {
                                    Tasks.await(recognizer.process(inputImage))
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            
                            if (visionText != null && visionText.text.isNotBlank()) {
                                extractedTexts.add(visionText.text)
                                val blocks = visionText.textBlocks
                                val lineHeights = blocks.flatMap { it.lines }.mapNotNull { it.boundingBox?.height() }
                                val avgLineHeight = if (lineHeights.isNotEmpty()) lineHeights.average() else 15.0
                                
                                blocks.forEach { block ->
                                    val para = doc.createParagraph()
                                    val blockText = block.text.trim()
                                    
                                    val firstLine = block.lines.firstOrNull()
                                    val firstLineText = firstLine?.text?.trim() ?: ""
                                    val blockHeight = firstLine?.boundingBox?.height() ?: 0
                                    
                                    val isHeading = blockHeight > avgLineHeight * 1.3 && blockText.length < 100 && !blockText.contains("\n")
                                    val isListItem = firstLineText.startsWith("•") || firstLineText.startsWith("-") || firstLineText.startsWith("*") || 
                                                     firstLineText.matches(Regex("^\\d+\\.\\s.*"))
                                    
                                    if (isHeading) {
                                        para.style = "Heading 1"
                                        val run = para.createRun()
                                        run.isBold = true
                                        run.fontSize = 16
                                        run.setText(blockText)
                                    } else if (isListItem) {
                                        val run = para.createRun()
                                        run.fontSize = 12
                                        run.setText(blockText)
                                    } else {
                                        val run = para.createRun()
                                        run.fontSize = 11
                                        val lines = block.lines
                                        lines.forEachIndexed { lineIndex, line ->
                                            run.setText(line.text)
                                            if (lineIndex < lines.size - 1) {
                                                run.addBreak()
                                            }
                                        }
                                    }
                                    para.spacingAfter = 200
                                }
                            } else {
                                // Fallback to Gemini OCR API
                                val geminiText = performGeminiOcr(imagePath, ocrLanguage)
                                if (!geminiText.isNullOrBlank()) {
                                    extractedTexts.add(geminiText)
                                    val paragraphs = geminiText.split(Regex("\\n\\s*\\n"))
                                    paragraphs.forEach { pText ->
                                        val trimmedP = pText.trim()
                                        if (trimmedP.isNotEmpty()) {
                                            val para = doc.createParagraph()
                                            val run = para.createRun()
                                            run.fontSize = 11
                                            
                                            val lines = trimmedP.split("\n")
                                            lines.forEachIndexed { lineIndex, line ->
                                                run.setText(line)
                                                if (lineIndex < lines.size - 1) {
                                                    run.addBreak()
                                                }
                                            }
                                            para.spacingAfter = 200
                                        }
                                    }
                                } else {
                                    // If absolutely no text can be extracted, fall back to inserting the JPEG image
                                    val para = doc.createParagraph()
                                    val run = para.createRun()
                                    
                                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeFile(imagePath, options)
                                    val imgWidth = options.outWidth
                                    val imgHeight = options.outHeight
                                    
                                    val targetWidth = 450
                                    val targetHeight = if (imgWidth > 0) (targetWidth * imgHeight / imgWidth) else 600
                                    
                                    java.io.FileInputStream(file).use { fis ->
                                        run.addPicture(
                                            fis,
                                            org.apache.poi.xwpf.usermodel.XWPFDocument.PICTURE_TYPE_JPEG,
                                            file.name,
                                            Units.toEMU(targetWidth.toDouble()),
                                            Units.toEMU(targetHeight.toDouble())
                                        )
                                    }
                                }
                            }
                            
                            if (index < savedImages.size - 1) {
                                val para = doc.createParagraph()
                                para.isPageBreak = true
                            }
                        }
                        
                        wordFile.outputStream().use { fos ->
                            doc.write(fos)
                        }
                        doc.close()
                        
                        savedDocPath = wordFile.absolutePath
                        if (extractedTexts.isNotEmpty()) {
                            finalOcrText = extractedTexts.joinToString("\n\n")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            ocrProgress.value = "Saving to database..."
            
            val totalSize = savedImages.sumOf { File(it).length() } + (savedDocPath?.let { File(it).length() } ?: 0L)
            val docNamePrefix = when (format) {
                OutputFormat.PDF -> "Scan"
                OutputFormat.JPEG -> "Scan_Img"
                OutputFormat.WORD -> "Scan_Doc"
            }
            
            val finalName = if (!customName.isNullOrBlank()) {
                customName.trim()
            } else {
                "${docNamePrefix}_${System.currentTimeMillis()}"
            }
            
            val doc = DocumentEntity(
                id = id,
                name = finalName,
                dateCreated = System.currentTimeMillis(),
                imagePaths = savedImages.joinToString(","),
                pdfPath = savedDocPath,
                ocrText = finalOcrText,
                sizeBytes = totalSize,
                folderId = folderId
            )
            repository.insertDocument(doc)
            
            ocrProgress.value = null
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun updateDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.updateDocument(doc)
        }
    }

    fun toggleFavorite(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.updateDocument(doc.copy(isFavorite = !doc.isFavorite))
        }
    }

    fun moveToTrash(id: String) {
        viewModelScope.launch {
            repository.moveToTrash(id)
        }
    }

    fun restoreFromTrash(id: String) {
        viewModelScope.launch {
            repository.restoreFromTrash(id)
        }
    }

    fun deletePermanently(doc: DocumentEntity) {
        viewModelScope.launch {
            // Delete files
            doc.imagePaths.split(",").forEach { path ->
                if (path.isNotEmpty()) {
                    File(path).delete()
                }
            }
            doc.pdfPath?.let { File(it).delete() }
            
            repository.deleteDocument(doc)
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.insertFolder(FolderEntity(name = name))
        }
    }

    fun updateFolder(folder: FolderEntity) {
        viewModelScope.launch {
            repository.updateFolder(folder)
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
        }
    }

    fun moveDocumentToFolder(documentId: String, folderId: String?) {
        viewModelScope.launch {
            val doc = repository.getDocumentById(documentId)
            if (doc != null) {
                repository.updateDocument(doc.copy(folderId = folderId))
            }
        }
    }

    private suspend fun performGeminiOcr(imagePath: String, languageCode: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(imagePath)
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val bytes = outputStream.toByteArray()
                val base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP)
                bitmap.recycle()

                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    return@withContext null
                }

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()

                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

                val prompt = if (languageCode == "ur") {
                    "Extract and output only the Urdu text from this image. Do not translate. Output only the exact words found in the image. No commentary, no explanations, no preamble, and no markdown formatting."
                } else {
                    "Extract and output only the text from this image. Do not translate. Output only the exact words found in the image. No commentary, no explanations, no preamble, and no markdown formatting."
                }

                val requestJson = JSONObject()
                val contentsArray = JSONArray()
                val contentObject = JSONObject()
                val partsArray = JSONArray()

                val textPart = JSONObject()
                textPart.put("text", prompt)

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

                val mediaType = "application/json".toMediaType()
                val requestBody = requestJson.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val responseBodyString = response.body?.string() ?: return@withContext null
                    val jsonResponse = JSONObject(responseBodyString)
                    val extractedText = jsonResponse.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    extractedText.trim()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun extractTextFromUri(context: Context, uri: Uri): String {
        val ocrLanguage = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("ocr_language", "en") ?: "en"
        var text: String? = null
        
        if (ocrLanguage != "ur") {
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val visionText = Tasks.await(recognizer.process(inputImage))
                text = visionText?.text
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        if (text.isNullOrBlank()) {
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    text = performGeminiOcr(file.absolutePath, ocrLanguage)
                }
            }
        }
        
        return text ?: ""
    }

    fun convertPdfToWord(
        pdfUri: Uri,
        onStart: () -> Unit,
        onProgress: (String) -> Unit,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onStart() }
            try {
                val app = getApplication<Application>()
                val contentResolver = app.contentResolver
                
                val pfd = try {
                    contentResolver.openFileDescriptor(pdfUri, "r")
                } catch (e: Exception) {
                    null
                }
                
                if (pfd == null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onFailure("Could not open PDF file. Make sure it's a valid local PDF.") }
                    return@launch
                }
                
                val renderer = try {
                    PdfRenderer(pfd)
                } catch (e: Exception) {
                    pfd.close()
                    null
                }
                
                if (renderer == null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onFailure("Could not read PDF. It might be corrupted or password-protected.") }
                    return@launch
                }
                
                val pageCount = renderer.pageCount
                if (pageCount == 0) {
                    renderer.close()
                    pfd.close()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onFailure("The PDF has 0 pages.") }
                    return@launch
                }
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onProgress("Found $pageCount pages. Initializing OCR...") }
                
                val doc = XWPFDocument()
                val extractedTexts = mutableListOf<String>()
                val savedImages = mutableListOf<String>()
                
                val sharedPrefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val ocrLanguage = sharedPrefs.getString("ocr_language", "en") ?: "en"
                
                val docId = UUID.randomUUID().toString()
                
                for (i in 0 until pageCount) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onProgress("Performing OCR (Page ${i + 1} of $pageCount)...") }
                    
                    val page = renderer.openPage(i)
                    // Render page into bitmap. Scale up 1.5x for OCR quality
                    val width = (page.width * 1.5).toInt()
                    val height = (page.height * 1.5).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    
                    val imageFile = File(app.filesDir, "pdf_page_${docId}_$i.jpg")
                    imageFile.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 65, out)
                    }
                    savedImages.add(imageFile.absolutePath)
                    
                    var pageText: String? = null
                    if (ocrLanguage != "ur") {
                        val inputImage = InputImage.fromFilePath(app, Uri.fromFile(imageFile))
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        try {
                            val visionText = Tasks.await(recognizer.process(inputImage))
                            if (visionText != null && visionText.text.isNotBlank()) {
                                pageText = visionText.text
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    if (pageText.isNullOrBlank()) {
                        pageText = performGeminiOcr(imageFile.absolutePath, ocrLanguage)
                    }
                    
                    if (!pageText.isNullOrBlank()) {
                        extractedTexts.add(pageText)
                        val paragraphs = pageText.split(Regex("\\n\\s*\\n"))
                        paragraphs.forEach { pText ->
                            val trimmedP = pText.trim()
                            if (trimmedP.isNotEmpty()) {
                                val para = doc.createParagraph()
                                val run = para.createRun()
                                run.fontSize = 11
                                
                                val lines = trimmedP.split("\n")
                                lines.forEachIndexed { lineIndex, line ->
                                    run.setText(line)
                                    if (lineIndex < lines.size - 1) {
                                        run.addBreak()
                                    }
                                }
                                para.spacingAfter = 200
                            }
                        }
                    } else {
                        val para = doc.createParagraph()
                        val run = para.createRun()
                        java.io.FileInputStream(imageFile).use { fis ->
                            run.addPicture(
                                fis,
                                org.apache.poi.xwpf.usermodel.XWPFDocument.PICTURE_TYPE_JPEG,
                                imageFile.name,
                                Units.toEMU(450.0),
                                Units.toEMU(600.0)
                            )
                        }
                    }
                    
                    if (i < pageCount - 1) {
                        val para = doc.createParagraph()
                        para.isPageBreak = true
                    }
                    
                    bitmap.recycle()
                }
                
                renderer.close()
                pfd.close()
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onProgress("Saving Word file...") }
                
                val wordFile = File(app.filesDir, "word_${docId}.docx")
                wordFile.outputStream().use { fos ->
                    doc.write(fos)
                }
                doc.close()
                
                var pdfName = "Converted_Document"
                contentResolver.query(pdfUri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            pdfName = name.substringBeforeLast(".")
                        }
                    }
                }
                
                val finalName = "${pdfName}_Converted"
                val totalSize = savedImages.sumOf { File(it).length() } + wordFile.length()
                val finalOcrText = if (extractedTexts.isNotEmpty()) extractedTexts.joinToString("\n\n") else null
                
                val docEntity = DocumentEntity(
                    id = docId,
                    name = finalName,
                    dateCreated = System.currentTimeMillis(),
                    imagePaths = savedImages.joinToString(","),
                    pdfPath = wordFile.absolutePath,
                    ocrText = finalOcrText,
                    sizeBytes = totalSize,
                    folderId = null
                )
                repository.insertDocument(docEntity)
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onSuccess(finalName) }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onFailure(e.localizedMessage ?: "Conversion failed.") }
            }
        }
    }
}
