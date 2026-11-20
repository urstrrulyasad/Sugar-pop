package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ArcadeGameMode
import com.example.data.MediaType
import com.example.data.SecurityPreferences
import com.example.data.VaultAlbumEntity
import com.example.data.VaultBackupManager
import com.example.data.VaultDatabase
import com.example.data.VaultMediaEntity
import com.example.data.VaultStorageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: VaultDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun appName_matchesSugarPop() {
        assertEquals("Sugar Pop", context.getString(R.string.app_name))
    }

    @Test
    fun securityPreferences_savesAndVerifiesSaltedPin() = runTest {
        val prefs = SecurityPreferences(context)
        prefs.savePin("2580")
        assertTrue(prefs.verifyPin("2580"))
        assertFalse(prefs.verifyPin("1234"))
    }

    @Test
    fun vaultDao_insertsMovesFavoritesAndDeletesMedia() = runTest {
        val dao = db.vaultDao()
        val album = VaultAlbumEntity(id = "alb_1", name = "Travel")
        dao.insertAlbum(album)

        val video = VaultMediaEntity(
            id = "vid_1",
            title = "Beach Sunset",
            mediaType = MediaType.VIDEO,
            mimeType = "video/mp4",
            vaultFilePath = "/tmp/vid_1.vault",
            thumbnailPath = null,
            fileSizeBytes = 4_500_000L,
            durationMs = 32_000L,
            width = 1920,
            height = 1080
        )
        dao.insertMedia(video)
        dao.setFavorite("vid_1", true)
        dao.moveItemsToAlbum(listOf("vid_1"), "alb_1")
        dao.updatePlaybackPosition("vid_1", 14_200L)

        val stored = dao.getMediaById("vid_1")!!
        assertTrue(stored.isFavorite)
        assertEquals("alb_1", stored.albumId)
        assertEquals(14_200L, stored.lastPositionMs)

        dao.deleteMediaByIds(listOf("vid_1"))
        assertTrue(dao.observeAllMedia().first().isEmpty())
    }

    @Test
    fun vaultBackupManager_exportsAndRestoresVaultData() = runTest {
        val dao = db.vaultDao()
        val storage = VaultStorageManager(context)
        val prefs = SecurityPreferences(context)
        val backupManager = VaultBackupManager(context, dao, storage, prefs)

        prefs.savePin("4321")
        prefs.saveCandyProgress(2400, 3)
        prefs.savePlatformerProgress(5100, 4)
        prefs.setSelectedGameMode(ArcadeGameMode.MATCH3)

        val mediaId = "backup_photo_1"
        val vaultFile = storage.vaultFileForId(mediaId)
        vaultFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        dao.insertAlbum(VaultAlbumEntity(id = "alb_b", name = "Private Docs"))
        dao.insertMedia(
            VaultMediaEntity(
                id = mediaId,
                title = "Secret Note",
                mediaType = MediaType.PHOTO,
                mimeType = "image/jpeg",
                vaultFilePath = vaultFile.absolutePath,
                thumbnailPath = null,
                albumId = "alb_b",
                fileSizeBytes = 5L
            )
        )

        val backupFile = File(context.cacheDir, "test_backup.zip")
        val backupUri = Uri.fromFile(backupFile)
        val exported = backupManager.exportToUri(backupUri)
        assertTrue(exported.success)
        assertEquals(1, exported.mediaCount)
        assertEquals(1, exported.albumCount)

        // Simulate wipe/reinstall
        storage.clearAllVaultFiles()
        dao.deleteAllMedia()
        dao.deleteAllAlbums()
        assertFalse(vaultFile.exists())
        assertTrue(dao.getAllMediaSnapshot().isEmpty())

        // Restore
        val restored = backupManager.restoreFromUri(backupUri)
        assertTrue(restored.success)
        assertTrue(restored.restoredPin)
        assertEquals(1, restored.mediaCount)
        assertEquals(1, restored.albumCount)
        assertTrue(vaultFile.exists())
        assertEquals(5L, vaultFile.length())
        assertTrue(prefs.verifyPin("4321"))
        val settings = prefs.settingsFlow.first()
        assertEquals(3, settings.candyLevel)
        assertEquals(4, settings.platformerLevel)
        assertEquals(ArcadeGameMode.MATCH3, settings.selectedGameMode)
    }
}
