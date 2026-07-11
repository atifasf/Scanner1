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

class DocumentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DocumentRepository
    private val context = application.applicationContext

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val allDocuments: StateFlow<List<DocumentEntity>>
    val favoriteDocuments: StateFlow<List<DocumentEntity>>
    val trashDocuments: StateFlow<List<DocumentEntity>>

    init {
        val documentDao = AppDatabase.getDatabase(application).documentDao()
        repository = DocumentRepository(documentDao)

        allDocuments = repository.allDocuments.combine(_searchQuery) { docs, query ->
            if (query.isBlank()) docs else docs.filter { it.name.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        favoriteDocuments = repository.favoriteDocuments.combine(_searchQuery) { docs, query ->
            if (query.isBlank()) docs else docs.filter { it.name.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        trashDocuments = repository.trashDocuments.combine(_searchQuery) { docs, query ->
            if (query.isBlank()) docs else docs.filter { it.name.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun getDocumentById(id: String): DocumentEntity? {
        return repository.getDocumentById(id)
    }

    fun saveScannedDocument(imageUris: List<Uri>, pdfUri: Uri?) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val savedImages = mutableListOf<String>()
            
            // Copy images to internal storage
            imageUris.forEachIndexed { index, uri ->
                val file = File(context.filesDir, "img_${id}_$index.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                savedImages.add(file.absolutePath)
            }

            var savedPdfPath: String? = null
            if (pdfUri != null) {
                val file = File(context.filesDir, "pdf_$id.pdf")
                context.contentResolver.openInputStream(pdfUri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                savedPdfPath = file.absolutePath
            }

            val doc = DocumentEntity(
                id = id,
                name = "Scan_${System.currentTimeMillis()}",
                dateCreated = System.currentTimeMillis(),
                imagePaths = savedImages.joinToString(","),
                pdfPath = savedPdfPath,
                ocrText = null,
                sizeBytes = savedImages.sumOf { File(it).length() } + (savedPdfPath?.let { File(it).length() } ?: 0L)
            )
            repository.insertDocument(doc)
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
}
