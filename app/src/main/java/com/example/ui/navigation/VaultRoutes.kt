package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface VaultRoute {
    @Serializable
    data object Home : VaultRoute

    @Serializable
    data object Albums : VaultRoute

    @Serializable
    data class AlbumDetail(val albumId: String) : VaultRoute

    @Serializable
    data object Settings : VaultRoute

    @Serializable
    data class PhotoViewer(val mediaId: String) : VaultRoute

    @Serializable
    data class VideoPlayer(val mediaId: String) : VaultRoute
}
