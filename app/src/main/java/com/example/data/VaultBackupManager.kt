package com.example.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupAlbumRecord(
    val id: String,
    val name: String,
    val createdAtMs: Long
)

@Serializable
data class BackupMediaRecord(
    val id: String,
    val title: String,
    val mediaType: String,
    val mimeType: String,
    val albumId: String? = null,
    val addedAtMs: Long,
    val fileSizeBytes: Long,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val isFavorite: Boolean = false,
    val lastPositionMs: Long = 0L,
    val hasThumbnail: Boolean = false
)

@Serializable
data class BackupSecurityRecord(
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val autoLockOnBackground: Boolean = true,
    val screenShieldEnabled: Boolean = true,
    val promptDeleteOriginal: Boolean = true,
    val themeMode: String = "DARK",
    val defaultPlaybackSpeed: Float = 1.0f,
    val candyHighScore: Int = 0,
    val candyLevel: Int = 1,
    val platformerHighScore: Int = 0,
    val platformerLevel: Int = 1,
    val selectedGameMode: String = ArcadeGameMode.PLATFORMER.name
)

@Serializable
data class VaultBackupManifest(
    val formatVersion: Int = 1,
    val exportedAtMs: Long = System.currentTimeMillis(),
    val security: BackupSecurityRecord,
    val albums: List<BackupAlbumRecord>,
    val media: List<BackupMediaRecord>
)

data class BackupOperationResult(
    val success: Boolean,
    val mediaCount: Int = 0,
    val albumCount: Int = 0,
    val restoredPin: Boolean = false
)

class VaultBackupManager(
    private val context: Context,
    private val dao: VaultDao,
    private val storage: VaultStorageManager,
    private val securityPreferences: SecurityPreferences
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportToUri(targetUri: Uri): BackupOperationResult = withContext(Dispatchers.IO) {
        runCatching {
            val albums = dao.getAllAlbumsSnapshot()
            val media = dao.getAllMediaSnapshot()
            val sec = securityPreferences.exportBackupSnapshot()

            val manifest = VaultBackupManifest(
                security = BackupSecurityRecord(
                    pinHash = sec.pinHash,
                    pinSalt = sec.pinSalt,
                    autoLockOnBackground = sec.autoLockOnBackground,
                    screenShieldEnabled = sec.screenShieldEnabled,
                    promptDeleteOriginal = sec.promptDeleteOriginal,
                    themeMode = sec.themeMode,
                    defaultPlaybackSpeed = sec.defaultPlaybackSpeed,
                    candyHighScore = sec.candyHighScore,
                    candyLevel = sec.candyLevel,
                    platformerHighScore = sec.platformerHighScore,
                    platformerLevel = sec.platformerLevel,
                    selectedGameMode = sec.selectedGameMode
                ),
                albums = albums.map { BackupAlbumRecord(it.id, it.name, it.createdAtMs) },
                media = media.map {
                    val thumbExists = !it.thumbnailPath.isNullOrBlank() && File(it.thumbnailPath).exists()
                    BackupMediaRecord(
                        id = it.id,
                        title = it.title,
                        mediaType = it.mediaType.name,
                        mimeType = it.mimeType,
                        albumId = it.albumId,
                        addedAtMs = it.addedAtMs,
                        fileSizeBytes = it.fileSizeBytes,
                        durationMs = it.durationMs,
                        width = it.width,
                        height = it.height,
                        isFavorite = it.isFavorite,
                        lastPositionMs = it.lastPositionMs,
                        hasThumbnail = thumbExists
                    )
                }
            )

            val outStream = context.contentResolver.openOutputStream(targetUri)
                ?: return@withContext BackupOperationResult(false)

            ZipOutputStream(BufferedOutputStream(outStream)).use { zip ->
                val manifestBytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifestBytes)
                zip.closeEntry()

                val buffer = ByteArray(65536)
                for (item in media) {
                    val mediaFile = File(item.vaultFilePath)
                    if (mediaFile.exists()) {
                        zip.putNextEntry(ZipEntry("media/${item.id}.vault"))
                        mediaFile.inputStream().use { input ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                zip.write(buffer, 0, read)
                            }
                        }
                        zip.closeEntry()
                    }
                    val thumbPath = item.thumbnailPath
                    if (!thumbPath.isNullOrBlank()) {
                        val thumbFile = File(thumbPath)
                        if (thumbFile.exists()) {
                            zip.putNextEntry(ZipEntry("thumbs/${item.id}.jpg"))
                            thumbFile.inputStream().use { input ->
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    zip.write(buffer, 0, read)
                                }
                            }
                            zip.closeEntry()
                        }
                    }
                }
            }

            BackupOperationResult(
                success = true,
                mediaCount = media.size,
                albumCount = albums.size
            )
        }.getOrDefault(BackupOperationResult(false))
    }

    suspend fun restoreFromUri(sourceUri: Uri): BackupOperationResult = withContext(Dispatchers.IO) {
        runCatching {
            val inStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext BackupOperationResult(false)

            var manifest: VaultBackupManifest? = null
            val extractedMediaIds = mutableSetOf<String>()
            val extractedThumbIds = mutableSetOf<String>()
            val buffer = ByteArray(65536)

            ZipInputStream(BufferedInputStream(inStream)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && !name.contains("..")) {
                        when {
                            name == "manifest.json" -> {
                                val bytes = zip.readBytes()
                                manifest = json.decodeFromString<VaultBackupManifest>(
                                    bytes.toString(Charsets.UTF_8)
                                )
                            }
                            name.startsWith("media/") && name.endsWith(".vault") -> {
                                val safeId = name.removePrefix("media/").removeSuffix(".vault")
                                    .replace(Regex("[^a-zA-Z0-9_-]"), "")
                                if (safeId.isNotEmpty()) {
                                    val target = storage.vaultFileForId(safeId)
                                    FileOutputStream(target).use { out ->
                                        while (true) {
                                            val read = zip.read(buffer)
                                            if (read <= 0) break
                                            out.write(buffer, 0, read)
                                        }
                                    }
                                    extractedMediaIds += safeId
                                }
                            }
                            name.startsWith("thumbs/") && name.endsWith(".jpg") -> {
                                val safeId = name.removePrefix("thumbs/").removeSuffix(".jpg")
                                    .replace(Regex("[^a-zA-Z0-9_-]"), "")
                                if (safeId.isNotEmpty()) {
                                    val target = storage.thumbFileForId(safeId)
                                    FileOutputStream(target).use { out ->
                                        while (true) {
                                            val read = zip.read(buffer)
                                            if (read <= 0) break
                                            out.write(buffer, 0, read)
                                        }
                                    }
                                    extractedThumbIds += safeId
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val parsed = manifest ?: return@withContext BackupOperationResult(false)

            val albumEntities = parsed.albums.map {
                VaultAlbumEntity(id = it.id, name = it.name, createdAtMs = it.createdAtMs)
            }
            if (albumEntities.isNotEmpty()) {
                dao.insertAlbumsBatch(albumEntities)
            }

            val mediaEntities = parsed.media.mapNotNull { rec ->
                val cleanId = rec.id.replace(Regex("[^a-zA-Z0-9_-]"), "")
                val mediaFile = storage.vaultFileForId(cleanId)
                if (!mediaFile.exists() && cleanId !in extractedMediaIds) {
                    return@mapNotNull null
                }
                val thumbFile = storage.thumbFileForId(cleanId)
                val thumbPath = if (thumbFile.exists() || cleanId in extractedThumbIds) {
                    thumbFile.absolutePath
                } else {
                    null
                }
                VaultMediaEntity(
                    id = rec.id,
                    title = rec.title,
                    mediaType = runCatching { MediaType.valueOf(rec.mediaType) }
                        .getOrDefault(MediaType.PHOTO),
                    mimeType = rec.mimeType,
                    vaultFilePath = mediaFile.absolutePath,
                    thumbnailPath = thumbPath,
                    albumId = rec.albumId,
                    addedAtMs = rec.addedAtMs,
                    fileSizeBytes = if (mediaFile.exists()) mediaFile.length() else rec.fileSizeBytes,
                    durationMs = rec.durationMs,
                    width = rec.width,
                    height = rec.height,
                    isFavorite = rec.isFavorite,
                    lastPositionMs = rec.lastPositionMs
                )
            }
            if (mediaEntities.isNotEmpty()) {
                dao.insertMediaBatch(mediaEntities)
            }

            val sec = parsed.security
            securityPreferences.restoreBackupSnapshot(
                SecurityBackupSnapshot(
                    pinHash = sec.pinHash,
                    pinSalt = sec.pinSalt,
                    autoLockOnBackground = sec.autoLockOnBackground,
                    screenShieldEnabled = sec.screenShieldEnabled,
                    promptDeleteOriginal = sec.promptDeleteOriginal,
                    themeMode = sec.themeMode,
                    defaultPlaybackSpeed = sec.defaultPlaybackSpeed,
                    candyHighScore = sec.candyHighScore,
                    candyLevel = sec.candyLevel,
                    platformerHighScore = sec.platformerHighScore,
                    platformerLevel = sec.platformerLevel,
                    selectedGameMode = sec.selectedGameMode
                )
            )

            BackupOperationResult(
                success = true,
                mediaCount = mediaEntities.size,
                albumCount = albumEntities.size,
                restoredPin = !sec.pinHash.isNullOrBlank()
            )
        }.getOrDefault(BackupOperationResult(false))
    }
}
