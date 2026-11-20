package com.example.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.MediaType
import com.example.ui.components.DeleteOriginalsDialog
import com.example.ui.components.HideMediaSourceSheet
import com.example.ui.navigation.VaultRoute
import com.example.ui.screens.AlbumDetailScreen
import com.example.ui.screens.AlbumsScreen
import com.example.ui.screens.CandyGameScreen
import com.example.ui.screens.PhotoViewerScreen
import com.example.ui.screens.PinLockScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VaultHomeScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.theme.KeepsakeVaultTheme
import kotlinx.coroutines.launch

private data class TopTab(
    val route: VaultRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun KeepsakeVaultApp(
    viewModel: VaultViewModel,
    onScreenShieldChanged: (Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var showImportSheet by remember { mutableStateOf(false) }
    var targetAlbumIdForImport by remember { mutableStateOf<String?>(null) }

    // Keep Window FLAG_SECURE in sync with security preference
    LaunchedEffect(uiState.securitySettings.screenShieldEnabled) {
        onScreenShieldChanged(uiState.securitySettings.screenShieldEnabled)
    }

    // Auto-lock vault when app goes to background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onAppBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Launchers for Zero-Permission Photo Picker, Document Picker, and MediaStore Delete Request
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importMediaUris(uris, targetAlbumIdForImport)
        }
        targetAlbumIdForImport = null
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importMediaUris(uris, targetAlbumIdForImport)
        }
        targetAlbumIdForImport = null
    }

    val deleteRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onSystemDeleteOriginalsResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is VaultEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }

                is VaultEvent.LaunchDeleteRequest -> {
                    runCatching {
                        val request = IntentSenderRequest.Builder(
                            event.outcome.pendingIntent.intentSender
                        ).build()
                        deleteRequestLauncher.launch(request)
                    }.onFailure {
                        viewModel.onSystemDeleteOriginalsResult(false)
                    }
                }
            }
        }
    }

    val launchAllMediaPicker: (String?) -> Unit = { albumId ->
        targetAlbumIdForImport = albumId
        viewModel.suppressNextAutoLock = true
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    val launchVideosPicker: (String?) -> Unit = { albumId ->
        targetAlbumIdForImport = albumId
        viewModel.suppressNextAutoLock = true
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    val launchPhotosPicker: (String?) -> Unit = { albumId ->
        targetAlbumIdForImport = albumId
        viewModel.suppressNextAutoLock = true
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val launchDocumentsPicker: (String?) -> Unit = { albumId ->
        targetAlbumIdForImport = albumId
        viewModel.suppressNextAutoLock = true
        documentPickerLauncher.launch(arrayOf("image/*", "video/*"))
    }

    KeepsakeVaultTheme(themeMode = uiState.securitySettings.themeMode) {
        val screenState = when {
            !uiState.isVaultOverlayOpen -> 0
            uiState.lockStatus != LockStatus.UNLOCKED -> 1
            else -> 2
        }

        Crossfade(
            targetState = screenState,
            label = "disguiseVaultCrossfade"
        ) { state ->
            when (state) {
                0 -> {
                    CandyGameScreen(
                        initialLevel = uiState.securitySettings.candyLevel,
                        highScore = uiState.securitySettings.candyHighScore,
                        hasPinConfigured = uiState.securitySettings.hasPinConfigured,
                        onSaveProgress = viewModel::saveCandyProgress,
                        onOpenSecretVault = viewModel::openSecretVault
                    )
                }

                1 -> {
                    androidx.activity.compose.BackHandler {
                        viewModel.cancelChangePin()
                    }
                    if (uiState.lockStatus == LockStatus.LOADING) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        )
                    } else {
                        PinLockScreen(
                            lockStatus = uiState.lockStatus,
                            pinEntry = uiState.pinEntry,
                            pinError = uiState.pinError,
                            isChangingPin = uiState.isChangingPin,
                            onDigit = viewModel::onPinDigit,
                            onBackspace = viewModel::onPinBackspace,
                            onClear = viewModel::onPinClear,
                            onCancelChangePin = viewModel::cancelChangePin
                        )
                    }
                }

                else -> {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                val tabs = remember {
                    listOf(
                        TopTab(
                            route = VaultRoute.Home,
                            labelRes = R.string.tab_vault,
                            selectedIcon = Icons.Rounded.PhotoLibrary,
                            unselectedIcon = Icons.Outlined.PhotoLibrary,
                            testTag = "nav_tab_vault"
                        ),
                        TopTab(
                            route = VaultRoute.Albums,
                            labelRes = R.string.tab_albums,
                            selectedIcon = Icons.Rounded.Folder,
                            unselectedIcon = Icons.Outlined.Folder,
                            testTag = "nav_tab_albums"
                        ),
                        TopTab(
                            route = VaultRoute.Settings,
                            labelRes = R.string.tab_settings,
                            selectedIcon = Icons.Rounded.Security,
                            unselectedIcon = Icons.Outlined.Security,
                            testTag = "nav_tab_settings"
                        )
                    )
                }

                val showNavigationChrome = currentDestination?.let { dest ->
                    dest.hasRoute<VaultRoute.Home>() ||
                        dest.hasRoute<VaultRoute.Albums>() ||
                        dest.hasRoute<VaultRoute.Settings>()
                } ?: true

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 600.dp

                    Row(modifier = Modifier.fillMaxSize()) {
                        if (useRail && showNavigationChrome) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Spacer(Modifier.weight(1f))
                                tabs.forEach { tab ->
                                    val selected = when (tab.route) {
                                        is VaultRoute.Home ->
                                            currentDestination?.hasRoute<VaultRoute.Home>() == true
                                        is VaultRoute.Albums ->
                                            currentDestination?.hasRoute<VaultRoute.Albums>() == true
                                        is VaultRoute.Settings ->
                                            currentDestination?.hasRoute<VaultRoute.Settings>() == true
                                        else -> false
                                    }
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(tab.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = stringResource(tab.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(tab.labelRes)) },
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                            }
                        }

                        Scaffold(
                            snackbarHost = {
                                SnackbarHost(
                                    hostState = snackbarHostState,
                                    modifier = Modifier.navigationBarsPadding()
                                )
                            },
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            bottomBar = {
                                if (!useRail && showNavigationChrome) {
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                        tabs.forEach { tab ->
                                            val selected = when (tab.route) {
                                                is VaultRoute.Home ->
                                                    currentDestination?.hasRoute<VaultRoute.Home>() == true
                                                is VaultRoute.Albums ->
                                                    currentDestination?.hasRoute<VaultRoute.Albums>() == true
                                                is VaultRoute.Settings ->
                                                    currentDestination?.hasRoute<VaultRoute.Settings>() == true
                                                else -> false
                                            }
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = {
                                                    navController.navigate(tab.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = stringResource(tab.labelRes)
                                                    )
                                                },
                                                label = { Text(stringResource(tab.labelRes)) },
                                                modifier = Modifier.testTag(tab.testTag)
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = VaultRoute.Home,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                composable<VaultRoute.Home> {
                                    VaultHomeScreen(
                                        uiState = uiState,
                                        onOpenMedia = { item ->
                                            if (item.mediaType == MediaType.VIDEO) {
                                                navController.navigate(VaultRoute.VideoPlayer(item.id))
                                            } else {
                                                navController.navigate(VaultRoute.PhotoViewer(item.id))
                                            }
                                        },
                                        onOpenImportSheet = {
                                            targetAlbumIdForImport = null
                                            showImportSheet = true
                                        },
                                        onQuickDocumentImport = {
                                            launchDocumentsPicker(null)
                                        },
                                        onFilterChange = viewModel::setFilter,
                                        onSortChange = viewModel::setSort,
                                        onSearchChange = viewModel::setSearchQuery,
                                        onToggleSelection = viewModel::toggleSelection,
                                        onSelectAll = viewModel::selectAllVisible,
                                        onClearSelection = viewModel::clearSelection,
                                        onMoveSelectedToAlbum = viewModel::moveSelectedToAlbum,
                                        onCreateAlbumAndMove = { name ->
                                            coroutineScope.launch {
                                                val created = viewModel.repository.createAlbum(name)
                                                if (created != null) {
                                                    viewModel.moveSelectedToAlbum(created.id)
                                                }
                                            }
                                        },
                                        onRestoreSelected = viewModel::restoreItemsToGallery,
                                        onDeleteSelected = viewModel::deleteItemsPermanently,
                                        onLockNow = viewModel::lockVaultNow
                                    )
                                }

                                composable<VaultRoute.Albums> {
                                    AlbumsScreen(
                                        albums = uiState.albums,
                                        allMedia = uiState.allMedia,
                                        onOpenAlbum = { albumId ->
                                            navController.navigate(VaultRoute.AlbumDetail(albumId))
                                        },
                                        onCreateAlbum = viewModel::createAlbum,
                                        onRenameAlbum = viewModel::renameAlbum,
                                        onDeleteAlbum = viewModel::deleteAlbum
                                    )
                                }

                                composable<VaultRoute.AlbumDetail> { backStackEntry ->
                                    val route = backStackEntry.toRoute<VaultRoute.AlbumDetail>()
                                    val album = uiState.albums.firstOrNull { it.id == route.albumId }
                                    AlbumDetailScreen(
                                        album = album,
                                        allMedia = uiState.allMedia,
                                        onBack = { navController.popBackStack() },
                                        onImportIntoAlbum = {
                                            targetAlbumIdForImport = route.albumId
                                            showImportSheet = true
                                        },
                                        onOpenMedia = { item ->
                                            if (item.mediaType == MediaType.VIDEO) {
                                                navController.navigate(VaultRoute.VideoPlayer(item.id))
                                            } else {
                                                navController.navigate(VaultRoute.PhotoViewer(item.id))
                                            }
                                        }
                                    )
                                }

                                composable<VaultRoute.Settings> {
                                    SettingsScreen(
                                        uiState = uiState,
                                        onChangePin = viewModel::startChangePin,
                                        onLockNow = viewModel::lockVaultNow,
                                        onAutoLockChanged = viewModel::setAutoLockOnBackground,
                                        onScreenShieldChanged = viewModel::setScreenShieldEnabled,
                                        onPromptDeleteOriginalChanged = viewModel::setPromptDeleteOriginal,
                                        onThemeModeChanged = viewModel::setThemeMode,
                                        onDefaultSpeedChanged = viewModel::setDefaultPlaybackSpeed,
                                        onWipeVault = viewModel::wipeEntireVault
                                    )
                                }

                                composable<VaultRoute.PhotoViewer> { backStackEntry ->
                                    val route = backStackEntry.toRoute<VaultRoute.PhotoViewer>()
                                    val photos = remember(uiState.allMedia) {
                                        uiState.allMedia.filter { it.mediaType == MediaType.PHOTO }
                                    }
                                    PhotoViewerScreen(
                                        initialMediaId = route.mediaId,
                                        photos = photos,
                                        albums = uiState.albums,
                                        onBack = { navController.popBackStack() },
                                        onToggleFavorite = viewModel::toggleFavorite,
                                        onMoveToAlbum = viewModel::moveItemToAlbum,
                                        onCreateAlbumAndMove = { name, mediaId ->
                                            coroutineScope.launch {
                                                val created = viewModel.repository.createAlbum(name)
                                                if (created != null) {
                                                    viewModel.moveItemToAlbum(mediaId, created.id)
                                                }
                                            }
                                        },
                                        onRenameMedia = viewModel::renameMedia,
                                        onRestoreToGallery = { photo, removeFromVault ->
                                            viewModel.restoreItemsToGallery(
                                                listOf(photo),
                                                removeFromVault
                                            )
                                        },
                                        onDeletePermanently = { photo ->
                                            viewModel.deleteItemsPermanently(listOf(photo))
                                        }
                                    )
                                }

                                composable<VaultRoute.VideoPlayer> { backStackEntry ->
                                    val route = backStackEntry.toRoute<VaultRoute.VideoPlayer>()
                                    val videos = remember(uiState.allMedia) {
                                        uiState.allMedia.filter { it.mediaType == MediaType.VIDEO }
                                    }
                                    VideoPlayerScreen(
                                        initialMediaId = route.mediaId,
                                        videos = videos,
                                        albums = uiState.albums,
                                        defaultSpeed = uiState.securitySettings.defaultPlaybackSpeed,
                                        onSavePosition = viewModel::updatePlaybackPosition,
                                        onCaptureFrameSnapshot = viewModel::captureVideoFrameSnapshot,
                                        onRenameMedia = viewModel::renameMedia,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }

                    if (showImportSheet) {
                        HideMediaSourceSheet(
                            onDismiss = { showImportSheet = false },
                            onPickPhotosAndVideos = { launchAllMediaPicker(targetAlbumIdForImport) },
                            onPickVideosOnly = { launchVideosPicker(targetAlbumIdForImport) },
                            onPickPhotosOnly = { launchPhotosPicker(targetAlbumIdForImport) },
                            onPickFromDocuments = { launchDocumentsPicker(targetAlbumIdForImport) }
                        )
                    }

                    if (uiState.pendingGalleryDeleteUris.isNotEmpty()) {
                        DeleteOriginalsDialog(
                            importedCount = uiState.lastImportedCount,
                            onRemoveOriginals = viewModel::confirmDeleteOriginalsFromGallery,
                            onKeepCopies = viewModel::dismissDeleteOriginalsPrompt
                        )
                    }
                }
                }
            }
        }
    }
}
