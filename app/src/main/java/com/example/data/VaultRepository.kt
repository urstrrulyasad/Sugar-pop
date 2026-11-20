package com.example.data

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class VaultRepository(
    private val dao: VaultDao,
    private val storage: VaultStorageManager,
    val securityPreferences: SecurityPreferences
) {
    val allMedia: Flow<List<VaultMediaEntity>> = dao.observeAllMedia()
    val allAlbums: Flow<List<VaultAlbumEntity>> = dao.observeAllAlbums()

    fun observeMediaById(id: String): Flow<VaultMediaEntity?> = dao.observeMediaById(id)

    suspend fun getMediaById(id: String): VaultMediaEntity? = dao.getMediaById(id)

    suspend fun getAllVideos(): List<VaultMediaEntity> = dao.getAllVideos()

    suspend fun getAllPhotos(): List<VaultMediaEntity> = dao.getAllPhotos()

    suspend fun importMedia(uris: List<Uri>, albumId: String?): List<VaultMediaEntity> {
        val imported = mutableListOf<VaultMediaEntity>()
        for (uri in uris) {
            val entity = storage.importFromUri(uri, albumId)
            if (entity != null) {
                imported += entity
            }
        }
        if (imported.isNotEmpty()) {
            dao.insertMediaBatch(imported)
        }
        return imported
    }

    suspend fun toggleFavorite(id: String, current: Boolean) {
        dao.setFavorite(id, !current)
    }

    suspend fun updatePlaybackPosition(id: String, positionMs: Long) {
        dao.updatePlaybackPosition(id, positionMs)
    }

    suspend fun moveToAlbum(ids: List<String>, albumId: String?) {
        if (ids.isNotEmpty()) {
            dao.moveItemsToAlbum(ids, albumId)
        }
    }

    suspend fun renameMedia(id: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isNotEmpty()) {
            dao.renameMedia(id, trimmed)
        }
    }

    suspend fun captureVideoFrameToVault(video: VaultMediaEntity, positionMs: Long): VaultMediaEntity? {
        val photo = storage.saveVideoFrameSnapshot(video, positionMs) ?: return null
        dao.insertMedia(photo)
        return photo
    }

    suspend fun restoreToGallery(items: List<VaultMediaEntity>, removeFromVault: Boolean): Int {
        var restoredCount = 0
        val toDelete = mutableListOf<VaultMediaEntity>()
        for (item in items) {
            if (storage.exportToPublicGallery(item)) {
                restoredCount++
                if (removeFromVault) {
                    toDelete += item
                }
            }
        }
        if (toDelete.isNotEmpty()) {
            deleteItems(toDelete)
        }
        return restoredCount
    }

    suspend fun deleteItems(items: List<VaultMediaEntity>) {
        if (items.isEmpty()) return
        for (item in items) {
            storage.deleteVaultFiles(item)
        }
        dao.deleteMediaByIds(items.map { it.id })
    }

    suspend fun createAlbum(name: String): VaultAlbumEntity? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val album = VaultAlbumEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed
        )
        dao.insertAlbum(album)
        return album
    }

    suspend fun renameAlbum(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            dao.renameAlbum(id, trimmed)
        }
    }

    suspend fun deleteAlbum(id: String) {
        dao.clearAlbumReference(id)
        dao.deleteAlbumById(id)
    }

    suspend fun wipeVault() {
        storage.clearAllVaultFiles()
        dao.deleteAllMedia()
        dao.deleteAllAlbums()
    }
}
