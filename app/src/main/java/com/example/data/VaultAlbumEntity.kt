package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_albums")
data class VaultAlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtMs: Long = System.currentTimeMillis()
)
