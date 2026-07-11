package com.example.data

import kotlinx.coroutines.flow.Flow

class DocumentRepository(private val documentDao: DocumentDao) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val favoriteDocuments: Flow<List<DocumentEntity>> = documentDao.getFavoriteDocuments()
    val trashDocuments: Flow<List<DocumentEntity>> = documentDao.getTrashDocuments()

    suspend fun getDocumentById(id: String): DocumentEntity? {
        return documentDao.getDocumentById(id)
    }

    suspend fun insertDocument(document: DocumentEntity) {
        documentDao.insertDocument(document)
    }

    suspend fun updateDocument(document: DocumentEntity) {
        documentDao.updateDocument(document)
    }

    suspend fun deleteDocument(document: DocumentEntity) {
        documentDao.deleteDocument(document)
    }

    suspend fun moveToTrash(id: String) {
        documentDao.setTrashStatus(id, true)
    }

    suspend fun restoreFromTrash(id: String) {
        documentDao.setTrashStatus(id, false)
    }
}
