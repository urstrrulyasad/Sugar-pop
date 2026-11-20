package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_media ORDER BY addedAtMs DESC")
    fun observeAllMedia(): Flow<List<VaultMediaEntity>>

    @Query("SELECT * FROM vault_media WHERE id = :id")
    fun observeMediaById(id: String): Flow<VaultMediaEntity?>

    @Query("SELECT * FROM vault_media WHERE id = :id")
    suspend fun getMediaById(id: String): VaultMediaEntity?

    @Query("SELECT * FROM vault_media WHERE mediaType = 'VIDEO' ORDER BY addedAtMs DESC")
    suspend fun getAllVideos(): List<VaultMediaEntity>

    @Query("SELECT * FROM vault_media WHERE mediaType = 'PHOTO' ORDER BY addedAtMs DESC")
    suspend fun getAllPhotos(): List<VaultMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(item: VaultMediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaBatch(items: List<VaultMediaEntity>)

    @Query("UPDATE vault_media SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE vault_media SET lastPositionMs = :positionMs WHERE id = :id")
    suspend fun updatePlaybackPosition(id: String, positionMs: Long)

    @Query("UPDATE vault_media SET albumId = :albumId WHERE id IN (:ids)")
    suspend fun moveItemsToAlbum(ids: List<String>, albumId: String?)

    @Query("UPDATE vault_media SET title = :title WHERE id = :id")
    suspend fun renameMedia(id: String, title: String)

    @Query("DELETE FROM vault_media WHERE id IN (:ids)")
    suspend fun deleteMediaByIds(ids: List<String>)

    @Query("DELETE FROM vault_media")
    suspend fun deleteAllMedia()

    // Albums
    @Query("SELECT * FROM vault_albums ORDER BY createdAtMs DESC")
    fun observeAllAlbums(): Flow<List<VaultAlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: VaultAlbumEntity)

    @Query("UPDATE vault_albums SET name = :name WHERE id = :id")
    suspend fun renameAlbum(id: String, name: String)

    @Query("UPDATE vault_media SET albumId = NULL WHERE albumId = :albumId")
    suspend fun clearAlbumReference(albumId: String)

    @Query("DELETE FROM vault_albums WHERE id = :id")
    suspend fun deleteAlbumById(id: String)

    @Query("DELETE FROM vault_albums")
    suspend fun deleteAllAlbums()
}
