package com.blockbuster.resource

import com.blockbuster.media.MediaItem
import com.blockbuster.media.MediaStore
import com.blockbuster.plugin.MediaPlugin
import com.blockbuster.plugin.MediaPluginManager
import com.blockbuster.theater.TheaterDeviceManager
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant

class PlayResourceTest {

    private lateinit var mediaStore: MediaStore
    private lateinit var pluginManager: MediaPluginManager
    private lateinit var theaterManager: TheaterDeviceManager
    private lateinit var playResource: PlayResource

    @BeforeEach
    fun setUp() {
        mediaStore = mock()
        pluginManager = mock()
        theaterManager = mock()
        playResource = PlayResource(mediaStore, pluginManager, theaterManager)
    }

    @Test
    fun `play with valid UUID returns 200`() {
        // Given
        val uuid = "test-uuid-123"
        val mediaItem = MediaItem(
            uuid = uuid,
            plugin = "roku",
            configJson = """{"channelId":"12","contentId":"81444554"}""",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val mockPlugin = mock<MediaPlugin<*>>()

        whenever(mediaStore.get(uuid)).thenReturn(mediaItem)
        whenever(pluginManager.getPlugin("roku")).thenReturn(mockPlugin)

        // When
        val response = playResource.play(uuid, null)

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)
        verify(mockPlugin).play(uuid)
        verify(theaterManager, never()).setupTheater(any())
    }

    @Test
    fun `play with deviceId triggers theater setup`() {
        // Given
        val uuid = "test-uuid-123"
        val deviceId = "living-room"
        val mediaItem = MediaItem(
            uuid = uuid,
            plugin = "roku",
            configJson = """{"channelId":"12","contentId":"81444554"}""",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val mockPlugin = mock<MediaPlugin<*>>()

        whenever(mediaStore.get(uuid)).thenReturn(mediaItem)
        whenever(pluginManager.getPlugin("roku")).thenReturn(mockPlugin)

        // When
        val response = playResource.play(uuid, deviceId)

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)
        verify(theaterManager).setupTheater(deviceId)
        verify(mockPlugin).play(uuid)
    }

    @Test
    fun `play with invalid UUID returns 404`() {
        // Given
        val uuid = "nonexistent-uuid"
        whenever(mediaStore.get(uuid)).thenReturn(null)

        // When
        val response = playResource.play(uuid, null)

        // Then
        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
        verify(pluginManager, never()).getPlugin(any())
        verify(theaterManager, never()).setupTheater(any())
    }

    @Test
    fun `play with missing plugin returns 404`() {
        // Given
        val uuid = "test-uuid-123"
        val mediaItem = MediaItem(
            uuid = uuid,
            plugin = "nonexistent-plugin",
            configJson = "{}",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        whenever(mediaStore.get(uuid)).thenReturn(mediaItem)
        whenever(pluginManager.getPlugin("nonexistent-plugin")).thenReturn(null)

        // When
        val response = playResource.play(uuid, null)

        // Then
        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
        verify(theaterManager, never()).setupTheater(any())
    }

    @Test
    fun `play with unknown deviceId returns 400`() {
        // Given
        val uuid = "test-uuid-123"
        val deviceId = "unknown-device"
        val mediaItem = MediaItem(
            uuid = uuid,
            plugin = "roku",
            configJson = "{}",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val mockPlugin = mock<MediaPlugin<*>>()

        whenever(mediaStore.get(uuid)).thenReturn(mediaItem)
        whenever(pluginManager.getPlugin("roku")).thenReturn(mockPlugin)
        whenever(theaterManager.setupTheater(deviceId)).thenThrow(IllegalArgumentException("Unknown device: $deviceId"))

        // When
        val response = playResource.play(uuid, deviceId)

        // Then
        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        verify(mockPlugin, never()).play(any())
    }

    @Test
    fun `play with plugin error returns 500`() {
        // Given
        val uuid = "test-uuid-123"
        val mediaItem = MediaItem(
            uuid = uuid,
            plugin = "roku",
            configJson = "{}",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val mockPlugin = mock<MediaPlugin<*>>()

        whenever(mediaStore.get(uuid)).thenReturn(mediaItem)
        whenever(pluginManager.getPlugin("roku")).thenReturn(mockPlugin)
        whenever(mockPlugin.play(any())).thenThrow(RuntimeException("Playback failed"))

        // When
        val response = playResource.play(uuid, null)

        // Then
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.statusCode, response.status)
    }

    @Test
    fun `playPage with valid UUID returns HTML`() {
        // Given
        val uuid = "test-uuid-123"
        val mediaItem = MediaItem(
            uuid = uuid,
            plugin = "roku",
            configJson = "{}",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        whenever(mediaStore.get(uuid)).thenReturn(mediaItem)

        // When
        val html = playResource.playPage(uuid)

        // Then
        assertTrue(html.contains("roku"))
        assertTrue(html.contains(uuid))
        assertTrue(html.contains("Play Now"))
    }

    @Test
    fun `playPage with invalid UUID returns error HTML`() {
        // Given
        val uuid = "nonexistent-uuid"
        whenever(mediaStore.get(uuid)).thenReturn(null)

        // When
        val html = playResource.playPage(uuid)

        // Then
        assertTrue(html.contains("Content Not Found"))
        assertTrue(html.contains(uuid))
    }
}
