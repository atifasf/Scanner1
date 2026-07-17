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

class DocumentViewModel(application: Application) : AndroidViewModel(application) {
    enum class OutputFormat { PDF, JPEG, WORD }

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
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            ocrProgress.value = "Optimizing images..."
            val app = getApplication<Application>()
            val id = UUID.randomUUID().toString()
            val savedImages = mutableListOf<String>()
            
            // Step 1: Intelligent resizing & compression
            imageUris.forEachIndexed { index, uri ->
                val file = File(app.filesDir, "img_${id}_$index.jpg")
                try {
                    val inputStream = app.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (originalBitmap != null) {
                        // Check if text or photo
                        val isText = isTextDocument(originalBitmap)
                        val maxDim = if (isText) 1600 else 2000
                        val quality = if (isText) 75 else 85
                        
                        // Resize
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
                        
                        savedImages.forEachIndexed { index, imagePath ->
                            ocrProgress.value = "Performing OCR (Page ${index + 1} of ${savedImages.size})..."
                            val file = File(imagePath)
                            val uri = Uri.fromFile(file)
                            val inputImage = InputImage.fromFilePath(app, uri)
                            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            
                            val visionText = try {
                                Tasks.await(recognizer.process(inputImage))
                            } catch (e: Exception) {
                                null
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
            onComplete()
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
}
