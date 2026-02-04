package com.blockbuster.resource

import com.blockbuster.media.MediaItem
import com.blockbuster.media.MediaStore
import com.blockbuster.plugin.MediaPlugin
import com.blockbuster.plugin.PluginRegistry
import com.blockbuster.theater.TheaterDeviceManager
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant

class PlayResourceTest {
    private lateinit var mediaStore: MediaStore
    private lateinit var plugins: MutableMap<String, MediaPlugin<*>>
    private lateinit var theaterManager: TheaterDeviceManager
    private lateinit var playResource: PlayResource

    @BeforeEach
    fun setUp() {
        mediaStore = mock()
        plugins = mutableMapOf()
        theaterManager = mock()
        playResource = PlayResource(mediaStore, PluginRegistry(plugins), theaterManager)
    }

    @Test
    fun `play with valid UUID returns 200`() {
        // Given
        val uuid = "test-uuid-123"
        val mediaItem =
            MediaItem(
                uuid = uuid,
                plugin = "roku",
                configJson = """{"channelId":"12","contentId":"81444554"}""",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        val mockPlugin = mock<MediaPlugin<*>>()

        whenever(mediaStore.get(uuid)).thenReturn(mediaItem)
        plugins["roku"] = mockPlugin

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
        val mediaItem =
            MediaItem(
                uuid = uuid,
                plugin = "roku",
                configJson = """{"channelId":"12","contentId":"81444554"}""",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        val mockPlugin = mock<MediaPlugin<*>>()

        whenever(mediaStore.get(uuid)).thenReturn(mediaItem)
        plugins["roku"] = mockPlugin

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
        verify(theaterManager, never()).setupTheater(any())
    }

    @Test
    fun `play with missing plugin returns 404`() {
        // Given
        val uuid = "test-uuid-123"
        val mediaItem =
            MediaItem(
                uuid = uuid,
                plugin = "nonexistent-plugin",
                configJson = "{}",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )

        whenever(mediaStore.get(uuid)).thenReturn(mediaItem)
        // plugins map does not contain "nonexistent-plugin"

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
        val mediaItem =
            MediaItem(
                uuid = uuid,
                plugin = "roku",
                configJson = "{}",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        val mockPlugin = mock<MediaPlugin<*>>()

        whenever(mediaStore.get(uuid)).thenReturn(mediaItem)
        plugins["roku"] = mockPlugin
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
        val mediaItem =
            MediaItem(
                uuid = uuid,
                plugin = "roku",
                configJson = "{}",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        val mockPlugin = mock<MediaPlugin<*>>()

        whenever(mediaStore.get(uuid)).thenReturn(mediaItem)
        plugins["roku"] = mockPlugin
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
        val mediaItem =
            MediaItem(
                uuid = uuid,
                plugin = "roku",
                configJson = "{}",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
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

    // ---------------------------------------------------------------
    // XSS security tests
    // ---------------------------------------------------------------

    @Test
    fun `playPage escapes HTML in UUID to prevent XSS for not-found content`() {
        // Given
        val xssUuid = "<script>alert(1)</script>"
        whenever(mediaStore.get(xssUuid)).thenReturn(null)

        // When
        val html = playResource.playPage(xssUuid)

        // Then - raw script tag must not appear; escaped form must be present
        assertFalse(html.contains("<script>"), "Raw <script> tag should be escaped")
        assertTrue(html.contains("&lt;script&gt;"), "Escaped script tag should be present")
    }

    @Test
    fun `playPage escapes HTML in UUID for valid content`() {
        // Given
        val xssUuid = "test\"><img src=x onerror=alert(1)>"
        val mediaItem =
            MediaItem(
                uuid = xssUuid,
                plugin = "roku",
                configJson = "{}",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        whenever(mediaStore.get(xssUuid)).thenReturn(mediaItem)

        // When
        val html = playResource.playPage(xssUuid)

        // Then — raw HTML tags must not appear; quotes must be escaped
        assertFalse(html.contains("<img"), "Raw <img> tag should be escaped")
        assertTrue(html.contains("&lt;img"), "Escaped <img> tag should be present")
        assertTrue(html.contains("&quot;"), "Double quotes should be escaped")
    }

    @Test
    fun `escapeHtml escapes all dangerous characters`() {
        val input = """<script>alert("XSS's")</script> & more"""
        val escaped = PlayResource.escapeHtml(input)

        assertFalse(escaped.contains("<"))
        assertFalse(escaped.contains(">"))
        assertFalse(escaped.contains("\""))
        assertTrue(escaped.contains("&lt;"))
        assertTrue(escaped.contains("&gt;"))
        assertTrue(escaped.contains("&quot;"))
        assertTrue(escaped.contains("&#x27;"))
        assertTrue(escaped.contains("&amp;"))
    }
}
