package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.MediaType
import com.example.data.VaultAlbumEntity
import com.example.data.VaultMediaEntity
import com.example.util.formatDate
import com.example.util.formatDuration
import com.example.util.formatFileSize
import com.example.util.formatResolution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HideMediaSourceSheet(
    onDismiss: () -> Unit,
    onPickPhotosAndVideos: () -> Unit,
    onPickVideosOnly: () -> Unit,
    onPickPhotosOnly: () -> Unit,
    onPickFromDocuments: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Hide into Keepsake Vault",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Text(
                text = "Files are copied into private internal storage shielded with .nomedia so system galleries cannot index them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            ImportOptionTile(
                icon = Icons.Rounded.Collections,
                title = "Photos & Videos (Photo Picker)",
                subtitle = "Zero-permission system picker for gallery media",
                testTag = "import_all_picker",
                onClick = {
                    onDismiss()
                    onPickPhotosAndVideos()
                }
            )
            ImportOptionTile(
                icon = Icons.Rounded.VideoLibrary,
                title = "Videos Only",
                subtitle = "Hide clips & play with the advanced Media3 player",
                testTag = "import_videos_picker",
                onClick = {
                    onDismiss()
                    onPickVideosOnly()
                }
            )
            ImportOptionTile(
                icon = Icons.Rounded.PhotoLibrary,
                title = "Photos Only",
                subtitle = "Hide private snapshots & documents",
                testTag = "import_photos_picker",
                onClick = {
                    onDismiss()
                    onPickPhotosOnly()
                }
            )
            ImportOptionTile(
                icon = Icons.Rounded.FolderOpen,
                title = "Import from Device Files",
                subtitle = "Browse Downloads & folders (supports auto-delete original)",
                testTag = "import_files_picker",
                onClick = {
                    onDismiss()
                    onPickFromDocuments()
                }
            )
        }
    }
}

@Composable
private fun ImportOptionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun DeleteOriginalsDialog(
    importedCount: Int,
    onRemoveOriginals: () -> Unit,
    onKeepCopies: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeepCopies,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Hidden $importedCount Item${if (importedCount == 1) "" else "s"} in Vault",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "Your media is now safely stored inside Keepsake Vault. Do you want to remove the original copies from your public gallery so they stay hidden?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onRemoveOriginals,
                modifier = Modifier.testTag("confirm_remove_originals_btn")
            ) {
                Text("Remove Originals")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onKeepCopies,
                modifier = Modifier.testTag("keep_originals_btn")
            ) {
                Text("Keep Copies")
            }
        }
    )
}

@Composable
fun AlbumPickerDialog(
    albums: List<VaultAlbumEntity>,
    onSelectAlbum: (String?) -> Unit,
    onCreateAndMove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newAlbumName by remember { mutableStateOf("") }
    var showCreateField by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DriveFileMove,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Move to Private Album") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        Surface(
                            onClick = { onSelectAlbum(null) },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Unfiled (Main Vault)",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                    items(albums, key = { it.id }) { album ->
                        Surface(
                            onClick = { onSelectAlbum(album.id) },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
                if (showCreateField) {
                    OutlinedTextField(
                        value = newAlbumName,
                        onValueChange = { newAlbumName = it },
                        label = { Text("New album name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TextButton(
                        onClick = { showCreateField = true },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("New Album")
                    }
                }
            }
        },
        confirmButton = {
            if (showCreateField) {
                Button(
                    onClick = {
                        if (newAlbumName.isNotBlank()) {
                            onCreateAndMove(newAlbumName.trim())
                        }
                    },
                    enabled = newAlbumName.isNotBlank()
                ) {
                    Text("Create & Move")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RestoreToGalleryDialog(
    count: Int,
    onConfirmRestore: (removeFromVault: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var removeFromVault by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Restore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Restore $count Item${if (count == 1) "" else "s"} to Gallery?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Exported files will reappear in your device's public Pictures/Movies folder.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { removeFromVault = !removeFromVault }
                ) {
                    Checkbox(
                        checked = removeFromVault,
                        onCheckedChange = { removeFromVault = it }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Remove from Keepsake Vault after restoring",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmRestore(removeFromVault) },
                modifier = Modifier.testTag("confirm_restore_btn")
            ) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ConfirmPermanentDeleteDialog(
    count: Int,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete $count Item${if (count == 1) "" else "s"} Forever?") },
        text = {
            Text(
                text = "This permanently erases the encrypted vault file(s). If you already removed the original from your gallery, they cannot be recovered.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_delete_btn")
            ) {
                Text("Delete Forever")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoBottomSheet(
    item: VaultMediaEntity,
    albumName: String?,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var titleDraft by remember(item.id) { mutableStateOf(item.title) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Media Details",
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedButton(onClick = { editing = !editing }) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (editing) "Cancel" else "Rename")
                }
            }

            if (editing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = titleDraft,
                        onValueChange = { titleDraft = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onRename(titleDraft)
                            editing = false
                        },
                        enabled = titleDraft.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow("Name", item.title)
            InfoRow("Type", if (item.mediaType == MediaType.VIDEO) "Private Video" else "Private Photo")
            InfoRow("Album", albumName ?: "Main Vault")
            InfoRow("Resolution", formatResolution(item.width, item.height))
            if (item.mediaType == MediaType.VIDEO && item.durationMs > 0L) {
                InfoRow("Duration", formatDuration(item.durationMs))
            }
            InfoRow("Size", formatFileSize(item.fileSizeBytes))
            InfoRow("Format", item.mimeType)
            InfoRow("Hidden On", formatDate(item.addedAtMs))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
