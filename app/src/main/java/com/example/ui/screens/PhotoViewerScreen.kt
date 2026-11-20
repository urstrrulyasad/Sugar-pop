package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.VaultAlbumEntity
import com.example.data.VaultMediaEntity
import com.example.ui.components.AlbumPickerDialog
import com.example.ui.components.ConfirmPermanentDeleteDialog
import com.example.ui.components.MediaInfoBottomSheet
import com.example.ui.components.RestoreToGalleryDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    initialMediaId: String,
    photos: List<VaultMediaEntity>,
    albums: List<VaultAlbumEntity>,
    onBack: () -> Unit,
    onToggleFavorite: (VaultMediaEntity) -> Unit,
    onMoveToAlbum: (String, String?) -> Unit,
    onCreateAlbumAndMove: (String, String) -> Unit,
    onRenameMedia: (String, String) -> Unit,
    onRestoreToGallery: (VaultMediaEntity, Boolean) -> Unit,
    onDeletePermanently: (VaultMediaEntity) -> Unit
) {
    if (photos.isEmpty()) {
        onBack()
        return
    }

    val initialIndex = remember(initialMediaId, photos) {
        photos.indexOfFirst { it.id == initialMediaId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    val currentPhoto = photos.getOrNull(pagerState.currentPage) ?: photos.first()

    var showControls by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showAlbumPicker by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            key = { index -> photos[index].id },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val photo = photos[page]
            ZoomablePhotoPage(
                photo = photo,
                onSingleTap = { showControls = !showControls }
            )
        }

        // Top Bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentPhoto.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${pagerState.currentPage + 1} of ${photos.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("photo_viewer_back_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showInfoSheet = true },
                        modifier = Modifier.testTag("photo_info_btn")
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = "Photo details",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.58f)
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }

        // Bottom Action Dock
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onToggleFavorite(currentPhoto) },
                        modifier = Modifier.testTag("photo_favorite_btn")
                    ) {
                        Icon(
                            imageVector = if (currentPhoto.isFavorite) {
                                Icons.Rounded.Favorite
                            } else {
                                Icons.Rounded.FavoriteBorder
                            },
                            contentDescription = "Favorite",
                            tint = if (currentPhoto.isFavorite) Color(0xFFFF6B8B) else Color.White
                        )
                    }
                    IconButton(
                        onClick = { showAlbumPicker = true },
                        modifier = Modifier.testTag("photo_move_album_btn")
                    ) {
                        Icon(
                            Icons.Rounded.DriveFileMove,
                            contentDescription = "Move to album",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier.testTag("photo_restore_btn")
                    ) {
                        Icon(
                            Icons.Rounded.Restore,
                            contentDescription = "Restore to gallery",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.testTag("photo_delete_btn")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteForever,
                            contentDescription = "Delete permanently",
                            tint = Color(0xFFF87171)
                        )
                    }
                }
            }
        }
    }

    if (showInfoSheet) {
        val albumName = albums.firstOrNull { it.id == currentPhoto.albumId }?.name
        MediaInfoBottomSheet(
            item = currentPhoto,
            albumName = albumName,
            onRename = { newName -> onRenameMedia(currentPhoto.id, newName) },
            onDismiss = { showInfoSheet = false }
        )
    }

    if (showAlbumPicker) {
        AlbumPickerDialog(
            albums = albums,
            onSelectAlbum = { albumId ->
                showAlbumPicker = false
                onMoveToAlbum(currentPhoto.id, albumId)
            },
            onCreateAndMove = { name ->
                showAlbumPicker = false
                onCreateAlbumAndMove(name, currentPhoto.id)
            },
            onDismiss = { showAlbumPicker = false }
        )
    }

    if (showRestoreDialog) {
        RestoreToGalleryDialog(
            count = 1,
            onConfirmRestore = { removeFromVault ->
                showRestoreDialog = false
                onRestoreToGallery(currentPhoto, removeFromVault)
                if (removeFromVault && photos.size <= 1) onBack()
            },
            onDismiss = { showRestoreDialog = false }
        )
    }

    if (showDeleteDialog) {
        ConfirmPermanentDeleteDialog(
            count = 1,
            onConfirmDelete = {
                showDeleteDialog = false
                onDeletePermanently(currentPhoto)
                if (photos.size <= 1) onBack()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun ZoomablePhotoPage(
    photo: VaultMediaEntity,
    onSingleTap: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4.5f)
        scale = newScale
        if (newScale > 1f) {
            offset += panChange
        } else {
            offset = Offset.Zero
        }
    }

    val imageRequest = remember(context, photo.vaultFilePath) {
        ImageRequest.Builder(context)
            .data(File(photo.vaultFilePath))
            .size(2048)
            .crossfade(true)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .then(
                if (scale > 1f) {
                    Modifier.transformable(state = transformState)
                } else {
                    Modifier.transformable(
                        state = transformState,
                        lockRotationOnZoomPan = true
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = photo.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
