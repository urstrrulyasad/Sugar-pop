package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.MediaType
import com.example.data.SecurityPreferences
import com.example.data.VaultAlbumEntity
import com.example.data.VaultDatabase
import com.example.data.VaultMediaEntity
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
}
