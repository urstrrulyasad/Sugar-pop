package com.example.util

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore

sealed interface OriginalDeleteOutcome {
    data class LaunchIntentSender(val pendingIntent: PendingIntent, val count: Int) : OriginalDeleteOutcome
    data class DeletedDirectly(val count: Int) : OriginalDeleteOutcome
    data class PickerProtectedOriginals(val count: Int) : OriginalDeleteOutcome
}

object GalleryDeleteHelper {

    fun prepareDeleteOriginals(context: Context, uris: List<Uri>): OriginalDeleteOutcome {
        if (uris.isEmpty()) return OriginalDeleteOutcome.DeletedDirectly(0)
        val resolver = context.contentResolver

        // 1. Collect deletable MediaStore URIs (Android 11+ createDeleteRequest)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mediaStoreUris = uris.mapNotNull { uri ->
                resolveMediaStoreUri(context, uri)
            }
            if (mediaStoreUris.isNotEmpty()) {
                val pendingIntent = runCatching {
                    MediaStore.createDeleteRequest(resolver, mediaStoreUris)
                }.getOrNull()
                if (pendingIntent != null) {
                    return OriginalDeleteOutcome.LaunchIntentSender(
                        pendingIntent = pendingIntent,
                        count = mediaStoreUris.size
                    )
                }
            }
        }

        // 2. Try SAF DocumentsContract deletion for files imported via "Import from Files"
        var deletedCount = 0
        for (uri in uris) {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val deleted = runCatching {
                    DocumentsContract.deleteDocument(resolver, uri)
                }.getOrDefault(false)
                if (deleted) deletedCount++
            }
        }
        if (deletedCount > 0) {
            return OriginalDeleteOutcome.DeletedDirectly(deletedCount)
        }

        // 3. Photo Picker URIs on Android 13+ are read-only grants without broad storage permissions
        return OriginalDeleteOutcome.PickerProtectedOriginals(uris.size)
    }

    private fun resolveMediaStoreUri(context: Context, uri: Uri): Uri? {
        if (uri.authority != "media") return null
        val path = uri.path.orEmpty()
        // Direct MediaStore URIs work directly with createDeleteRequest
        if (!path.contains("/picker")) {
            return uri
        }
        // On some Android versions, MediaStore.getMediaUri may resolve if permitted; otherwise returns null safely
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
        } else {
            null
        }
    }
}
