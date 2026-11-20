package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ArcadeGameMode
import com.example.data.MediaType
import com.example.data.SecurityPreferences
import com.example.data.VaultAlbumEntity
import com.example.data.VaultBackupManager
import com.example.data.VaultDatabase
import com.example.data.VaultMediaEntity
import com.example.data.VaultRepository
import com.example.data.VaultSecuritySettings
import com.example.data.VaultStorageManager
import com.example.ui.theme.ThemeMode
import com.example.util.GalleryDeleteHelper
import com.example.util.OriginalDeleteOutcome
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LockStatus {
    LOADING,
    SETUP_PIN,
    CONFIRM_PIN,
    LOCKED,
    UNLOCKED
}

enum class MediaFilter(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    PHOTOS("Photos"),
    FAVORITES("Favorites")
}

enum class MediaSort(val label: String) {
    NEWEST("Newest"),
    LARGEST("Largest"),
    DURATION("Duration"),
    NAME("Name")
}

data class VaultUiState(
    val isVaultOverlayOpen: Boolean = false,
    val lockStatus: LockStatus = LockStatus.LOADING,
    val pinEntry: String = "",
    val pinError: String? = null,
    val isChangingPin: Boolean = false,
    val securitySettings: VaultSecuritySettings = VaultSecuritySettings(),
    val allMedia: List<VaultMediaEntity> = emptyList(),
    val filteredMedia: List<VaultMediaEntity> = emptyList(),
    val albums: List<VaultAlbumEntity> = emptyList(),
    val activeFilter: MediaFilter = MediaFilter.ALL,
    val activeSort: MediaSort = MediaSort.NEWEST,
    val searchQuery: String = "",
    val selectedIds: Set<String> = emptySet(),
    val isImporting: Boolean = false,
    val pendingGalleryDeleteUris: List<Uri> = emptyList(),
    val lastImportedCount: Int = 0
) {
    val totalPhotos: Int get() = allMedia.count { it.mediaType == MediaType.PHOTO }
    val totalVideos: Int get() = allMedia.count { it.mediaType == MediaType.VIDEO }
    val totalSizeBytes: Long get() = allMedia.sumOf { it.fileSizeBytes }
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

sealed interface VaultEvent {
    data class ShowMessage(val message: String) : VaultEvent
    data class LaunchDeleteRequest(val outcome: OriginalDeleteOutcome.LaunchIntentSender) : VaultEvent
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val database = VaultDatabase.getInstance(application)
    private val storage = VaultStorageManager(application)
    private val securityPreferences = SecurityPreferences(application)
    private val backupManager = VaultBackupManager(
        application,
        database.vaultDao(),
        storage,
        securityPreferences
    )
    val repository = VaultRepository(database.vaultDao(), storage, securityPreferences)

    private val _isVaultOverlayOpen = MutableStateFlow(false)
    private val _lockStatus = MutableStateFlow(LockStatus.LOADING)
    private val _pinEntry = MutableStateFlow("")
    private val _pinError = MutableStateFlow<String?>(null)
    private val _isChangingPin = MutableStateFlow(false)
    private var pendingSetupPin: String? = null

    private val _activeFilter = MutableStateFlow(MediaFilter.ALL)
    private val _activeSort = MutableStateFlow(MediaSort.NEWEST)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isImporting = MutableStateFlow(false)
    private val _pendingDeleteUris = MutableStateFlow<List<Uri>>(emptyList())
    private val _lastImportedCount = MutableStateFlow(0)

    private val _events = MutableSharedFlow<VaultEvent>()
    val events: SharedFlow<VaultEvent> = _events.asSharedFlow()

    // Prevents auto-locking while the user is picking media or confirming system delete dialog
    var suppressNextAutoLock: Boolean = false

    private val lockSlice = combine(
        _isVaultOverlayOpen,
        _lockStatus,
        _pinEntry,
        _pinError,
        _isChangingPin,
        securityPreferences.settingsFlow
    ) { values ->
        LockSlice(
            isVaultOverlayOpen = values[0] as Boolean,
            status = values[1] as LockStatus,
            pinEntry = values[2] as String,
            pinError = values[3] as String?,
            isChangingPin = values[4] as Boolean,
            settings = values[5] as VaultSecuritySettings
        )
    }

    private val querySlice = combine(
        _activeFilter,
        _activeSort,
        _searchQuery,
        _selectedIds,
        _isImporting
    ) { filter, sort, query, selected, importing ->
        QuerySlice(filter, sort, query, selected, importing)
    }

    val uiState: StateFlow<VaultUiState> = combine(
        lockSlice,
        querySlice,
        repository.allMedia,
        repository.allAlbums,
        _pendingDeleteUris,
        _lastImportedCount
    ) { values ->
        val lock = values[0] as LockSlice
        val query = values[1] as QuerySlice
        @Suppress("UNCHECKED_CAST")
        val media = values[2] as List<VaultMediaEntity>
        @Suppress("UNCHECKED_CAST")
        val albums = values[3] as List<VaultAlbumEntity>
        @Suppress("UNCHECKED_CAST")
        val pendingUris = values[4] as List<Uri>
        val importedCount = values[5] as Int

        val filtered = applyFilterAndSort(
            media = media,
            filter = query.filter,
            sort = query.sort,
            searchQuery = query.searchQuery
        )

        VaultUiState(
            isVaultOverlayOpen = lock.isVaultOverlayOpen,
            lockStatus = lock.status,
            pinEntry = lock.pinEntry,
            pinError = lock.pinError,
            isChangingPin = lock.isChangingPin,
            securitySettings = lock.settings,
            allMedia = media,
            filteredMedia = filtered,
            albums = albums,
            activeFilter = query.filter,
            activeSort = query.sort,
            searchQuery = query.searchQuery,
            selectedIds = query.selectedIds,
            isImporting = query.isImporting,
            pendingGalleryDeleteUris = pendingUris,
            lastImportedCount = importedCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultUiState()
    )

    init {
        viewModelScope.launch {
            val settings = securityPreferences.settingsFlow.first()
            _lockStatus.value = if (settings.hasPinConfigured) {
                LockStatus.LOCKED
            } else {
                LockStatus.SETUP_PIN
            }
        }
    }

    // --- PIN Lock Actions ---
    fun onPinDigit(digit: String) {
        val current = _pinEntry.value
        if (current.length >= 4) return
        _pinError.value = null
        val updated = current + digit
        _pinEntry.value = updated

        if (updated.length == 4) {
            handleCompletedPin(updated)
        }
    }

    fun onPinBackspace() {
        _pinError.value = null
        if (_pinEntry.value.isNotEmpty()) {
            _pinEntry.value = _pinEntry.value.dropLast(1)
        }
    }

    fun onPinClear() {
        _pinError.value = null
        _pinEntry.value = ""
    }

    fun openSecretVault() {
        viewModelScope.launch {
            val settings = securityPreferences.settingsFlow.first()
            _pinEntry.value = ""
            _pinError.value = null
            _isChangingPin.value = false
            _lockStatus.value = if (settings.hasPinConfigured) {
                LockStatus.LOCKED
            } else {
                LockStatus.SETUP_PIN
            }
            _isVaultOverlayOpen.value = true
        }
    }

    fun returnToGameDisguise() {
        _pinEntry.value = ""
        _pinError.value = null
        _selectedIds.value = emptySet()
        _isChangingPin.value = false
        _isVaultOverlayOpen.value = false
        viewModelScope.launch {
            val settings = securityPreferences.settingsFlow.first()
            _lockStatus.value = if (settings.hasPinConfigured) {
                LockStatus.LOCKED
            } else {
                LockStatus.SETUP_PIN
            }
        }
    }

    fun saveCandyProgress(score: Int, level: Int) {
        viewModelScope.launch {
            securityPreferences.saveCandyProgress(score, level)
        }
    }

    fun savePlatformerProgress(score: Int, level: Int) {
        viewModelScope.launch {
            securityPreferences.savePlatformerProgress(score, level)
        }
    }

    fun setSelectedGameMode(mode: ArcadeGameMode) {
        viewModelScope.launch {
            securityPreferences.setSelectedGameMode(mode)
        }
    }

    fun exportVaultBackup(targetUri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = backupManager.exportToUri(targetUri)
            _isImporting.value = false
            if (result.success) {
                _events.emit(
                    VaultEvent.ShowMessage(
                        "Backup saved (${result.mediaCount} media, ${result.albumCount} albums)"
                    )
                )
            } else {
                _events.emit(VaultEvent.ShowMessage("Could not export vault backup."))
            }
        }
    }

    fun restoreVaultBackup(sourceUri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = backupManager.restoreFromUri(sourceUri)
            _isImporting.value = false
            if (result.success) {
                if (result.restoredPin && _lockStatus.value != LockStatus.UNLOCKED) {
                    _pinEntry.value = ""
                    _pinError.value = null
                    _lockStatus.value = LockStatus.LOCKED
                }
                _events.emit(
                    VaultEvent.ShowMessage(
                        "Restored ${result.mediaCount} media & ${result.albumCount} albums!"
                    )
                )
            } else {
                _events.emit(VaultEvent.ShowMessage("Invalid or unreadable backup archive."))
            }
        }
    }

    fun startChangePin() {
        pendingSetupPin = null
        _pinEntry.value = ""
        _pinError.value = null
        _isChangingPin.value = true
        _lockStatus.value = LockStatus.SETUP_PIN
    }

    fun cancelChangePin() {
        if (_isChangingPin.value) {
            pendingSetupPin = null
            _pinEntry.value = ""
            _pinError.value = null
            _isChangingPin.value = false
            _lockStatus.value = LockStatus.UNLOCKED
        } else {
            returnToGameDisguise()
        }
    }

    fun lockVaultNow() {
        returnToGameDisguise()
    }

    fun onAppBackgrounded() {
        if (suppressNextAutoLock) {
            suppressNextAutoLock = false
            return
        }
        val settings = uiState.value.securitySettings
        if (settings.autoLockOnBackground && _isVaultOverlayOpen.value) {
            returnToGameDisguise()
        }
    }

    private fun handleCompletedPin(pin: String) {
        viewModelScope.launch {
            when (_lockStatus.value) {
                LockStatus.SETUP_PIN -> {
                    pendingSetupPin = pin
                    _pinEntry.value = ""
                    _lockStatus.value = LockStatus.CONFIRM_PIN
                }

                LockStatus.CONFIRM_PIN -> {
                    if (pin == pendingSetupPin) {
                        securityPreferences.savePin(pin)
                        pendingSetupPin = null
                        _pinEntry.value = ""
                        val wasChanging = _isChangingPin.value
                        _isChangingPin.value = false
                        _lockStatus.value = LockStatus.UNLOCKED
                        if (wasChanging) {
                            _events.emit(VaultEvent.ShowMessage("Vault PIN updated"))
                        }
                    } else {
                        pendingSetupPin = null
                        _pinEntry.value = ""
                        _pinError.value = "PINs did not match. Please try again."
                        _lockStatus.value = LockStatus.SETUP_PIN
                    }
                }

                LockStatus.LOCKED -> {
                    if (securityPreferences.verifyPin(pin)) {
                        _pinEntry.value = ""
                        _pinError.value = null
                        _lockStatus.value = LockStatus.UNLOCKED
                    } else {
                        _pinEntry.value = ""
                        _pinError.value = "Incorrect PIN. Try again."
                    }
                }

                else -> Unit
            }
        }
    }

    // --- Media Import & Gallery Hiding ---
    fun importMediaUris(uris: List<Uri>, albumId: String? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            val imported = repository.importMedia(uris, albumId)
            _isImporting.value = false

            if (imported.isEmpty()) {
                _events.emit(VaultEvent.ShowMessage("Could not import the selected media."))
                return@launch
            }

            _lastImportedCount.value = imported.size
            val shouldPromptDelete = uiState.value.securitySettings.promptDeleteOriginal
            if (shouldPromptDelete) {
                _pendingDeleteUris.value = uris
            } else {
                _events.emit(
                    VaultEvent.ShowMessage(
                        "Hidden ${imported.size} item${if (imported.size == 1) "" else "s"} in Keepsake Vault"
                    )
                )
            }
        }
    }

    fun confirmDeleteOriginalsFromGallery() {
        val uris = _pendingDeleteUris.value
        _pendingDeleteUris.value = emptyList()
        if (uris.isEmpty()) return

        viewModelScope.launch {
            when (val outcome = GalleryDeleteHelper.prepareDeleteOriginals(getApplication(), uris)) {
                is OriginalDeleteOutcome.LaunchIntentSender -> {
                    suppressNextAutoLock = true
                    _events.emit(VaultEvent.LaunchDeleteRequest(outcome))
                }

                is OriginalDeleteOutcome.DeletedDirectly -> {
                    _events.emit(
                        VaultEvent.ShowMessage(
                            "Hidden in vault & removed ${outcome.count} original${if (outcome.count == 1) "" else "s"} from device"
                        )
                    )
                }

                is OriginalDeleteOutcome.PickerProtectedOriginals -> {
                    _events.emit(
                        VaultEvent.ShowMessage(
                            "Hidden ${outcome.count} item(s) in vault! Android Photo Picker protects originals—delete from your Gallery app or use 'Import from Files' to auto-remove originals."
                        )
                    )
                }
            }
        }
    }

    fun dismissDeleteOriginalsPrompt() {
        val count = _lastImportedCount.value
        _pendingDeleteUris.value = emptyList()
        viewModelScope.launch {
            _events.emit(
                VaultEvent.ShowMessage(
                    "Hidden $count item${if (count == 1) "" else "s"} in Keepsake Vault"
                )
            )
        }
    }

    fun onSystemDeleteOriginalsResult(deleted: Boolean) {
        viewModelScope.launch {
            if (deleted) {
                _events.emit(
                    VaultEvent.ShowMessage("Original media removed from public Gallery!")
                )
            } else {
                _events.emit(
                    VaultEvent.ShowMessage("Kept in vault. Original was left in public Gallery.")
                )
            }
        }
    }

    // --- Filtering, Sorting, Selection ---
    fun setFilter(filter: MediaFilter) {
        _activeFilter.value = filter
    }

    fun setSort(sort: MediaSort) {
        _activeSort.value = sort
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSelection(id: String) {
        _selectedIds.update { current ->
            if (id in current) current - id else current + id
        }
    }

    fun selectAllVisible() {
        _selectedIds.value = uiState.value.filteredMedia.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    // --- Item & Album Mutations ---
    fun toggleFavorite(item: VaultMediaEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(item.id, item.isFavorite)
        }
    }

    fun renameMedia(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameMedia(id, newTitle)
            _events.emit(VaultEvent.ShowMessage("Renamed item"))
        }
    }

    fun updatePlaybackPosition(id: String, positionMs: Long) {
        viewModelScope.launch {
            repository.updatePlaybackPosition(id, positionMs)
        }
    }

    fun captureVideoFrameSnapshot(video: VaultMediaEntity, positionMs: Long) {
        viewModelScope.launch {
            val saved = repository.captureVideoFrameToVault(video, positionMs)
            if (saved != null) {
                _events.emit(VaultEvent.ShowMessage("Captured frame to Photo Vault!"))
            } else {
                _events.emit(VaultEvent.ShowMessage("Could not capture video frame"))
            }
        }
    }

    fun moveSelectedToAlbum(albumId: String?) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.moveToAlbum(ids, albumId)
            _selectedIds.value = emptySet()
            _events.emit(VaultEvent.ShowMessage("Moved ${ids.size} item(s) to album"))
        }
    }

    fun moveItemToAlbum(mediaId: String, albumId: String?) {
        viewModelScope.launch {
            repository.moveToAlbum(listOf(mediaId), albumId)
            _events.emit(VaultEvent.ShowMessage("Updated album"))
        }
    }

    fun restoreItemsToGallery(items: List<VaultMediaEntity>, removeFromVault: Boolean) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val count = repository.restoreToGallery(items, removeFromVault)
            _selectedIds.value = emptySet()
            if (count > 0) {
                val suffix = if (removeFromVault) "and removed from vault" else "(copy kept in vault)"
                _events.emit(VaultEvent.ShowMessage("Restored $count item(s) to Gallery $suffix"))
            } else {
                _events.emit(VaultEvent.ShowMessage("Failed to restore items to Gallery"))
            }
        }
    }

    fun deleteItemsPermanently(items: List<VaultMediaEntity>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.deleteItems(items)
            _selectedIds.value = emptySet()
            _events.emit(VaultEvent.ShowMessage("Permanently deleted ${items.size} item(s)"))
        }
    }

    fun createAlbum(name: String) {
        viewModelScope.launch {
            val created = repository.createAlbum(name)
            if (created != null) {
                _events.emit(VaultEvent.ShowMessage("Created album \"${created.name}\""))
            }
        }
    }

    fun renameAlbum(albumId: String, newName: String) {
        viewModelScope.launch {
            repository.renameAlbum(albumId, newName)
            _events.emit(VaultEvent.ShowMessage("Album renamed"))
        }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch {
            repository.deleteAlbum(albumId)
            _events.emit(VaultEvent.ShowMessage("Album removed (media kept in Vault)"))
        }
    }

    // --- Security Settings ---
    fun setAutoLockOnBackground(enabled: Boolean) {
        viewModelScope.launch { securityPreferences.setAutoLockOnBackground(enabled) }
    }

    fun setScreenShieldEnabled(enabled: Boolean) {
        viewModelScope.launch { securityPreferences.setScreenShieldEnabled(enabled) }
    }

    fun setPromptDeleteOriginal(enabled: Boolean) {
        viewModelScope.launch { securityPreferences.setPromptDeleteOriginal(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { securityPreferences.setThemeMode(mode) }
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        viewModelScope.launch { securityPreferences.setDefaultPlaybackSpeed(speed) }
    }

    fun wipeEntireVault() {
        viewModelScope.launch {
            repository.wipeVault()
            _selectedIds.value = emptySet()
            _events.emit(VaultEvent.ShowMessage("All hidden media wiped permanently"))
        }
    }

    private fun applyFilterAndSort(
        media: List<VaultMediaEntity>,
        filter: MediaFilter,
        sort: MediaSort,
        searchQuery: String
    ): List<VaultMediaEntity> {
        val byFilter = when (filter) {
            MediaFilter.ALL -> media
            MediaFilter.VIDEOS -> media.filter { it.mediaType == MediaType.VIDEO }
            MediaFilter.PHOTOS -> media.filter { it.mediaType == MediaType.PHOTO }
            MediaFilter.FAVORITES -> media.filter { it.isFavorite }
        }
        val bySearch = if (searchQuery.isBlank()) {
            byFilter
        } else {
            val q = searchQuery.trim()
            byFilter.filter { it.title.contains(q, ignoreCase = true) }
        }
        return when (sort) {
            MediaSort.NEWEST -> bySearch.sortedByDescending { it.addedAtMs }
            MediaSort.LARGEST -> bySearch.sortedByDescending { it.fileSizeBytes }
            MediaSort.DURATION -> bySearch.sortedByDescending { it.durationMs }
            MediaSort.NAME -> bySearch.sortedBy { it.title.lowercase() }
        }
    }

    private data class LockSlice(
        val isVaultOverlayOpen: Boolean,
        val status: LockStatus,
        val pinEntry: String,
        val pinError: String?,
        val isChangingPin: Boolean,
        val settings: VaultSecuritySettings
    )

    private data class QuerySlice(
        val filter: MediaFilter,
        val sort: MediaSort,
        val searchQuery: String,
        val selectedIds: Set<String>,
        val isImporting: Boolean
    )
}
