package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.MediaType
import com.example.data.VaultMediaEntity
import com.example.ui.MediaFilter
import com.example.ui.MediaSort
import com.example.ui.VaultUiState
import com.example.ui.components.AlbumPickerDialog
import com.example.ui.components.ConfirmPermanentDeleteDialog
import com.example.ui.components.RestoreToGalleryDialog
import com.example.ui.components.VaultMediaGrid
import com.example.util.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultHomeScreen(
    uiState: VaultUiState,
    onOpenMedia: (VaultMediaEntity) -> Unit,
    onOpenImportSheet: () -> Unit,
    onQuickDocumentImport: () -> Unit,
    onFilterChange: (MediaFilter) -> Unit,
    onSortChange: (MediaSort) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMoveSelectedToAlbum: (String?) -> Unit,
    onCreateAlbumAndMove: (String) -> Unit,
    onRestoreSelected: (List<VaultMediaEntity>, Boolean) -> Unit,
    onDeleteSelected: (List<VaultMediaEntity>) -> Unit,
    onLockNow: () -> Unit
) {
    var searchExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showAlbumPicker by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val selectedMedia = remember(uiState.allMedia, uiState.selectedIds) {
        uiState.allMedia.filter { it.id in uiState.selectedIds }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${uiState.selectedIds.size} Selected",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onClearSelection,
                            modifier = Modifier.testTag("clear_selection_btn")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onSelectAll,
                            modifier = Modifier.testTag("select_all_btn")
                        ) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(
                            onClick = { showAlbumPicker = true },
                            modifier = Modifier.testTag("move_to_album_btn")
                        ) {
                            Icon(Icons.Rounded.DriveFileMove, contentDescription = "Move to album")
                        }
                        IconButton(
                            onClick = { showRestoreDialog = true },
                            modifier = Modifier.testTag("restore_selected_btn")
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = "Restore to gallery")
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("delete_selected_btn")
                        ) {
                            Icon(
                                Icons.Rounded.DeleteForever,
                                contentDescription = "Delete permanently",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Private Vault",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "${uiState.totalVideos} Videos · ${uiState.totalPhotos} Photos · ${formatFileSize(uiState.totalSizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                searchExpanded = !searchExpanded
                                if (!searchExpanded) onSearchChange("")
                            },
                            modifier = Modifier.testTag("search_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (searchExpanded) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = "Search vault"
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { sortMenuExpanded = true },
                                modifier = Modifier.testTag("sort_menu_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort media")
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                MediaSort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = sort.label,
                                                fontWeight = if (sort == uiState.activeSort) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.Normal
                                                },
                                                color = if (sort == uiState.activeSort) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        },
                                        onClick = {
                                            onSortChange(sort)
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onLockNow,
                            modifier = Modifier.testTag("lock_now_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Lock and return to Sugar Pop",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }

            if (uiState.isImporting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = searchExpanded && !uiState.isSelectionMode) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search hidden photos & videos...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("vault_search_input")
                )
            }

            // Filter chips row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MediaFilter.entries) { filter ->
                    val selected = uiState.activeFilter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterChange(filter) },
                        label = { Text(filter.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                    )
                }
            }

            if (uiState.filteredMedia.isEmpty()) {
                VaultEmptyState(
                    hasAnyMedia = uiState.allMedia.isNotEmpty(),
                    onOpenImportSheet = onOpenImportSheet,
                    onQuickDocumentImport = onQuickDocumentImport,
                    onResetFilters = {
                        onFilterChange(MediaFilter.ALL)
                        onSearchChange("")
                    }
                )
            } else {
                VaultMediaGrid(
                    items = uiState.filteredMedia,
                    selectedIds = uiState.selectedIds,
                    isSelectionMode = uiState.isSelectionMode,
                    onItemClick = { item ->
                        if (uiState.isSelectionMode) {
                            onToggleSelection(item.id)
                        } else {
                            onOpenMedia(item)
                        }
                    },
                    onItemLongClick = { item ->
                        onToggleSelection(item.id)
                    },
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = 96.dp
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (!uiState.isSelectionMode) {
            ExtendedFloatingActionButton(
                onClick = onOpenImportSheet,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Hide Media") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("hide_media_fab")
            )
        }
    }

    if (showAlbumPicker) {
        AlbumPickerDialog(
            albums = uiState.albums,
            onSelectAlbum = { albumId ->
                showAlbumPicker = false
                onMoveSelectedToAlbum(albumId)
            },
            onCreateAndMove = { name ->
                showAlbumPicker = false
                onCreateAlbumAndMove(name)
            },
            onDismiss = { showAlbumPicker = false }
        )
    }

    if (showRestoreDialog) {
        RestoreToGalleryDialog(
            count = selectedMedia.size,
            onConfirmRestore = { removeFromVault ->
                showRestoreDialog = false
                onRestoreSelected(selectedMedia, removeFromVault)
            },
            onDismiss = { showRestoreDialog = false }
        )
    }

    if (showDeleteDialog) {
        ConfirmPermanentDeleteDialog(
            count = selectedMedia.size,
            onConfirmDelete = {
                showDeleteDialog = false
                onDeleteSelected(selectedMedia)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun VaultEmptyState(
    hasAnyMedia: Boolean,
    onOpenImportSheet: () -> Unit,
    onQuickDocumentImport: () -> Unit,
    onResetFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!hasAnyMedia) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                ) {
                    Box {
                        Image(
                            painter = painterResource(id = R.drawable.img_vault_empty_1795185625838),
                            contentDescription = "Protected Media Vault Illustration",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.4f to androidx.compose.ui.graphics.Color.Transparent,
                                        1.0f to MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VaultBadgeChip(Icons.Rounded.PhotoLibrary, "Gallery Hidden")
                            VaultBadgeChip(Icons.Rounded.Videocam, "Media3 Pro Player")
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.empty_vault_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.empty_vault_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onOpenImportSheet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("empty_hide_media_btn")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.hide_media_cta))
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onQuickDocumentImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("empty_import_files_btn")
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.hide_from_files_cta))
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No matching items",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Try clearing your filter or search query.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onResetFilters) {
                    Text("Show All Hidden Media")
                }
            }
        }
    }
}

@Composable
private fun VaultBadgeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
