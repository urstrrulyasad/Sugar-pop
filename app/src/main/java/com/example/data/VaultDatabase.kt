package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class VaultConverters {
    @TypeConverter
    fun fromMediaType(type: MediaType): String = type.name

    @TypeConverter
    fun toMediaType(value: String): MediaType =
        runCatching { MediaType.valueOf(value) }.getOrDefault(MediaType.PHOTO)
}

@Database(
    entities = [VaultMediaEntity::class, VaultAlbumEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(VaultConverters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        fun getInstance(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "keepsake_vault.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
