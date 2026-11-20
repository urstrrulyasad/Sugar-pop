package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class VaultStorageManager(private val context: Context) {

    private val mediaDir: File by lazy {
        File(context.filesDir, "vault_media").apply {
            if (!exists()) mkdirs()
            ensureNoMedia(this)
        }
    }

    private val thumbsDir: File by lazy {
        File(context.filesDir, "vault_thumbs").apply {
            if (!exists()) mkdirs()
            ensureNoMedia(this)
        }
    }

    private fun ensureNoMedia(dir: File) {
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) {
            runCatching { noMedia.createNewFile() }
        }
    }

    suspend fun importFromUri(uri: Uri, albumId: String?): VaultMediaEntity? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val rawMime = resolver.getType(uri) ?: "application/octet-stream"
            val displayInfo = queryDisplayNameAndSize(uri)
            val fileName = displayInfo.first ?: "Hidden_${System.currentTimeMillis()}"

            val mediaType = when {
                rawMime.startsWith("video/") -> MediaType.VIDEO
                rawMime.startsWith("image/") -> MediaType.PHOTO
                fileName.endsWith(".mp4", true) ||
                    fileName.endsWith(".mkv", true) ||
                    fileName.endsWith(".mov", true) ||
                    fileName.endsWith(".webm", true) ||
                    fileName.endsWith(".3gp", true) -> MediaType.VIDEO
                else -> MediaType.PHOTO
            }

            val resolvedMime = when {
                rawMime != "application/octet-stream" -> rawMime
                mediaType == MediaType.VIDEO -> "video/mp4"
                else -> "image/jpeg"
            }

            val id = UUID.randomUUID().toString()
            val destFile = File(mediaDir, "$id.vault")

            val copied = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output, bufferSize = 65536)
                    }
                    true
                } ?: false
            }.getOrDefault(false)

            if (!copied || !destFile.exists() || destFile.length() == 0L) {
                destFile.delete()
                return@withContext null
            }

            val fileSize = destFile.length()
            val thumbFile = File(thumbsDir, "$id.jpg")

            var width = 0
            var height = 0
            var durationMs = 0L
            var thumbPath: String? = null

            if (mediaType == MediaType.VIDEO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(destFile.absolutePath)
                    durationMs = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull() ?: 0L
                    width = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                    )?.toIntOrNull() ?: 0
                    height = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                    )?.toIntOrNull() ?: 0

                    val frameTimeUs = if (durationMs > 1500L) 1_000_000L else 0L
                    val frame = retriever.getFrameAtTime(
                        frameTimeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ) ?: retriever.getFrameAtTime(0L)
                    if (frame != null) {
                        saveScaledThumbnail(frame, thumbFile, maxDim = 640)
                        frame.recycle()
                        if (thumbFile.exists()) thumbPath = thumbFile.absolutePath
                    }
                } catch (_: Throwable) {
                    // Metadata extraction may fail on exotic codecs; playback can still proceed.
                } finally {
                    runCatching { retriever.release() }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(destFile.absolutePath, bounds)
                width = bounds.outWidth.coerceAtLeast(0)
                height = bounds.outHeight.coerceAtLeast(0)

                val sampleSize = calculateInSampleSize(width, height, reqWidth = 540, reqHeight = 540)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bmp = BitmapFactory.decodeFile(destFile.absolutePath, decodeOpts)
                if (bmp != null) {
                    saveScaledThumbnail(bmp, thumbFile, maxDim = 540)
                    bmp.recycle()
                    if (thumbFile.exists()) thumbPath = thumbFile.absolutePath
                }
            }

            VaultMediaEntity(
                id = id,
                title = cleanTitle(fileName),
                mediaType = mediaType,
                mimeType = resolvedMime,
                vaultFilePath = destFile.absolutePath,
                thumbnailPath = thumbPath,
                albumId = albumId,
                fileSizeBytes = fileSize,
                durationMs = durationMs,
                width = width,
                height = height
            )
        }

    suspend fun saveVideoFrameSnapshot(
        video: VaultMediaEntity,
        positionMs: Long
    ): VaultMediaEntity? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(video.vaultFilePath)
            val bitmap = retriever.getFrameAtTime(
                positionMs.coerceAtLeast(0L) * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: retriever.getFrameAtTime(positionMs.coerceAtLeast(0L) * 1000L)
                ?: return@withContext null

            val id = UUID.randomUUID().toString()
            val destFile = File(mediaDir, "$id.vault")
            val thumbFile = File(thumbsDir, "$id.jpg")

            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
            }
            saveScaledThumbnail(bitmap, thumbFile, maxDim = 540)
            val width = bitmap.width
            val height = bitmap.height
            bitmap.recycle()

            VaultMediaEntity(
                id = id,
                title = "${video.title}_frame_${positionMs / 1000}s",
                mediaType = MediaType.PHOTO,
                mimeType = "image/jpeg",
                vaultFilePath = destFile.absolutePath,
                thumbnailPath = if (thumbFile.exists()) thumbFile.absolutePath else null,
                albumId = video.albumId,
                fileSizeBytes = destFile.length(),
                durationMs = 0L,
                width = width,
                height = height
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun exportToPublicGallery(media: VaultMediaEntity): Boolean =
        withContext(Dispatchers.IO) {
            val source = File(media.vaultFilePath)
            if (!source.exists()) return@withContext false

            val resolver = context.contentResolver
            val extension = when {
                media.mimeType.contains("png", true) -> "png"
                media.mimeType.contains("webp", true) -> "webp"
                media.mimeType.contains("mkv", true) -> "mkv"
                media.mimeType.contains("webm", true) -> "webm"
                media.mediaType == MediaType.VIDEO -> "mp4"
                else -> "jpg"
            }
            val safeTitle = media.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val displayName = "${safeTitle}_${System.currentTimeMillis()}.$extension"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = if (media.mediaType == MediaType.VIDEO) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                val relativePath = if (media.mediaType == MediaType.VIDEO) {
                    "${Environment.DIRECTORY_MOVIES}/KeepsakeRestored"
                } else {
                    "${Environment.DIRECTORY_PICTURES}/KeepsakeRestored"
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, media.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val targetUri = resolver.insert(collection, values) ?: return@withContext false
                return@withContext runCatching {
                    resolver.openOutputStream(targetUri)?.use { out ->
                        source.inputStream().use { input -> input.copyTo(out, 65536) }
                    }
                    val update = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(targetUri, update, null, null)
                    true
                }.getOrElse {
                    runCatching { resolver.delete(targetUri, null, null) }
                    false
                }
            } else {
                val collection = if (media.mediaType == MediaType.VIDEO) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, media.mimeType)
                }
                val targetUri = resolver.insert(collection, values) ?: return@withContext false
                return@withContext runCatching {
                    resolver.openOutputStream(targetUri)?.use { out ->
                        source.inputStream().use { input -> input.copyTo(out, 65536) }
                    }
                    true
                }.getOrDefault(false)
            }
        }

    suspend fun deleteVaultFiles(media: VaultMediaEntity) = withContext(Dispatchers.IO) {
        runCatching { File(media.vaultFilePath).delete() }
        media.thumbnailPath?.let { runCatching { File(it).delete() } }
    }

    suspend fun clearAllVaultFiles() = withContext(Dispatchers.IO) {
        mediaDir.listFiles()?.forEach { file ->
            if (file.name != ".nomedia") file.delete()
        }
        thumbsDir.listFiles()?.forEach { file ->
            if (file.name != ".nomedia") file.delete()
        }
    }

    private fun queryDisplayNameAndSize(uri: Uri): Pair<String?, Long?> {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else null
                    name to size
                } else {
                    null to null
                }
            } ?: (null to null)
        }.getOrDefault(null to null)
    }

    private fun saveScaledThumbnail(source: Bitmap, dest: File, maxDim: Int) {
        val ratio = minOf(
            maxDim.toFloat() / source.width.coerceAtLeast(1),
            maxDim.toFloat() / source.height.coerceAtLeast(1),
            1f
        )
        val targetW = (source.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (source.height * ratio).toInt().coerceAtLeast(1)
        val scaled = if (ratio < 1f) {
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        } else {
            source
        }
        FileOutputStream(dest).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 86, out)
        }
        if (scaled !== source) scaled.recycle()
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun cleanTitle(raw: String): String {
        val dot = raw.lastIndexOf('.')
        return if (dot > 0) raw.substring(0, dot) else raw
    }
}
