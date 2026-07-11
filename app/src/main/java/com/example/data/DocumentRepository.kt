package com.example.data

import kotlinx.coroutines.flow.Flow

class DocumentRepository(
    private val documentDao: DocumentDao,
    private val folderDao: FolderDao
) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val favoriteDocuments: Flow<List<DocumentEntity>> = documentDao.getFavoriteDocuments()
    val trashDocuments: Flow<List<DocumentEntity>> = documentDao.getTrashDocuments()
    val allFolders: Flow<List<FolderEntity>> = folderDao.getAllFolders()

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

    suspend fun insertFolder(folder: FolderEntity) {
        folderDao.insertFolder(folder)
    }

    suspend fun updateFolder(folder: FolderEntity) {
        folderDao.updateFolder(folder)
    }

    suspend fun deleteFolder(folder: FolderEntity) {
        folderDao.deleteFolder(folder)
    }
}
