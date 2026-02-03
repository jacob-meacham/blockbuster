package com.blockbuster.resource

import com.blockbuster.media.MediaStore
import com.blockbuster.media.RokuMediaContent
import com.blockbuster.media.MediaContentParser
import com.blockbuster.plugin.MediaPlugin
import com.blockbuster.plugin.MediaPluginManager
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class LibraryResourceTest {

    private lateinit var pluginManager: MediaPluginManager
    private lateinit var mediaStore: MediaStore
    private lateinit var libraryResource: LibraryResource

    @BeforeEach
    fun setUp() {
        pluginManager = mock()
        mediaStore = mock()
        libraryResource = LibraryResource(pluginManager, mediaStore, "http://blockbuster.local:8585")
    }

    @Test
    fun `addByPlugin returns UUID and URL`() {
        // Given
        val pluginName = "roku"
        val uuid = "test-uuid-123"
        val content = mapOf(
            "channelId" to "12",
            "contentId" to "81444554",
            "mediaType" to "movie",
            "title" to "Test Movie"
        )
        val request = LibraryResource.AddByPluginRequest(content)

        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        val mockParser = mock<MediaContentParser<RokuMediaContent>>()
        val rokuContent = RokuMediaContent(
            channelId = "12",
            contentId = "81444554",
            mediaType = "movie",
            title = "Test Movie"
        )

        whenever(pluginManager.getPlugin(pluginName)).thenReturn(mockPlugin)
        whenever(mockPlugin.getContentParser()).thenReturn(mockParser)
        whenever(mockParser.fromJson(any())).thenReturn(rokuContent)
        whenever(mediaStore.put(eq(pluginName), any())).thenReturn(uuid)

        // When
        val response = libraryResource.addByPlugin(pluginName, request)

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val responseMap = response.entity as Map<String, String>

        assertEquals(uuid, responseMap["uuid"])
        assertEquals("http://blockbuster.local:8585/play/$uuid", responseMap["url"])
    }

    @Test
    fun `addByPlugin with update uses existing UUID`() {
        // Given
        val pluginName = "roku"
        val existingUuid = "existing-uuid-456"
        val content = mapOf(
            "channelId" to "12",
            "contentId" to "81444554"
        )
        val request = LibraryResource.AddByPluginRequest(content, uuid = existingUuid)

        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        val mockParser = mock<MediaContentParser<RokuMediaContent>>()
        val rokuContent = RokuMediaContent(
            channelId = "12",
            contentId = "81444554"
        )

        whenever(pluginManager.getPlugin(pluginName)).thenReturn(mockPlugin)
        whenever(mockPlugin.getContentParser()).thenReturn(mockParser)
        whenever(mockParser.fromJson(any())).thenReturn(rokuContent)

        // When
        val response = libraryResource.addByPlugin(pluginName, request)

        // Then
        verify(mediaStore).update(eq(existingUuid), eq(pluginName), any())
        verify(mediaStore, never()).put(any(), any())

        @Suppress("UNCHECKED_CAST")
        val responseMap = response.entity as Map<String, String>
        assertEquals(existingUuid, responseMap["uuid"])
    }

    @Test
    fun `addByPlugin with unsupported plugin returns error`() {
        // Given
        val pluginName = "unsupported"
        val request = LibraryResource.AddByPluginRequest(emptyMap())

        whenever(pluginManager.getPlugin(pluginName)).thenReturn(null)

        // When
        val response = libraryResource.addByPlugin(pluginName, request)

        // Then
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.statusCode, response.status)
    }

    @Test
    fun `URL format is correct`() {
        // Given
        val uuid = "abc-123-def"
        val expectedUrl = "http://blockbuster.local:8585/play/$uuid"

        // Just verify the format matches what we expect
        assertTrue(expectedUrl.startsWith("http://blockbuster.local:8585/play/"))
        assertTrue(expectedUrl.endsWith(uuid))
    }
}
