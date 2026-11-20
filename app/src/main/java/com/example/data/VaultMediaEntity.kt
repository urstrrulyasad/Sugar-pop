package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MediaType {
    PHOTO,
    VIDEO
}

@Entity(tableName = "vault_media")
data class VaultMediaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val mediaType: MediaType,
    val mimeType: String,
    val vaultFilePath: String,
    val thumbnailPath: String?,
    val albumId: String? = null,
    val fileSizeBytes: Long,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val lastPositionMs: Long = 0L,
    val isFavorite: Boolean = false,
    val addedAtMs: Long = System.currentTimeMillis()
)
