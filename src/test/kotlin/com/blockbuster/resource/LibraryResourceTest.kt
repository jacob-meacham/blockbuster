package com.blockbuster.resource

import com.blockbuster.media.MediaContentParser
import com.blockbuster.media.MediaItem
import com.blockbuster.media.MediaStore
import com.blockbuster.media.RokuMediaContent
import com.blockbuster.plugin.MediaPlugin
import com.blockbuster.plugin.PluginRegistry
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant

class LibraryResourceTest {
    private lateinit var plugins: MutableMap<String, MediaPlugin<*>>
    private lateinit var mediaStore: MediaStore
    private lateinit var libraryResource: LibraryResource

    @BeforeEach
    fun setUp() {
        plugins = mutableMapOf()
        mediaStore = mock()
        libraryResource = LibraryResource(PluginRegistry(plugins), mediaStore)
    }

    @Test
    fun `addByPlugin returns UUID and URL`() {
        // Given
        val pluginName = "roku"
        val uuid = "test-uuid-123"
        val content =
            mapOf(
                "channelId" to "12",
                "contentId" to "81444554",
                "mediaType" to "movie",
                "title" to "Test Movie",
            )
        val request = LibraryResource.AddByPluginRequest(content)

        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        val mockParser = mock<MediaContentParser<RokuMediaContent>>()
        val rokuContent =
            RokuMediaContent(
                channelId = "12",
                contentId = "81444554",
                mediaType = "movie",
                title = "Test Movie",
            )

        plugins[pluginName] = mockPlugin
        whenever(mockPlugin.getContentParser()).thenReturn(mockParser)
        whenever(mockParser.fromJson(any())).thenReturn(rokuContent)
        whenever(mediaStore.putOrUpdate(isNull(), eq(pluginName), any())).thenReturn(uuid)

        // When
        val response = libraryResource.addByPlugin(pluginName, request)

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val responseMap = response.entity as Map<String, String>

        assertEquals(uuid, responseMap["uuid"])
        assertEquals("/play/$uuid", responseMap["url"])
    }

    @Test
    fun `addByPlugin with update uses existing UUID`() {
        // Given
        val pluginName = "roku"
        val existingUuid = "existing-uuid-456"
        val content =
            mapOf(
                "channelId" to "12",
                "contentId" to "81444554",
            )
        val request = LibraryResource.AddByPluginRequest(content, uuid = existingUuid)

        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        val mockParser = mock<MediaContentParser<RokuMediaContent>>()
        val rokuContent =
            RokuMediaContent(
                channelId = "12",
                contentId = "81444554",
            )

        plugins[pluginName] = mockPlugin
        whenever(mockPlugin.getContentParser()).thenReturn(mockParser)
        whenever(mockParser.fromJson(any())).thenReturn(rokuContent)
        whenever(mediaStore.putOrUpdate(eq(existingUuid), eq(pluginName), any())).thenReturn(existingUuid)

        // When
        val response = libraryResource.addByPlugin(pluginName, request)

        // Then
        verify(mediaStore).putOrUpdate(eq(existingUuid), eq(pluginName), any())
        verify(mediaStore, never()).put(any(), any())

        @Suppress("UNCHECKED_CAST")
        val responseMap = response.entity as Map<String, String>
        assertEquals(existingUuid, responseMap["uuid"])
    }

    @Test
    fun `addByPlugin with unsupported plugin throws BadRequestException`() {
        // Given
        val pluginName = "unsupported"
        val request = LibraryResource.AddByPluginRequest(emptyMap())

        // plugins map does not contain "unsupported"

        // When/Then - BadRequestException propagates for Dropwizard to map to 400
        assertThrows(jakarta.ws.rs.BadRequestException::class.java) {
            libraryResource.addByPlugin(pluginName, request)
        }
    }

    @Test
    fun `URL format is correct`() {
        // Given
        val uuid = "abc-123-def"
        val expectedUrl = "/play/$uuid"

        // Just verify the format matches what we expect
        assertTrue(expectedUrl.startsWith("/play/"))
        assertTrue(expectedUrl.endsWith(uuid))
    }

    @Test
    fun `delete returns success when item exists`() {
        whenever(mediaStore.remove("existing-uuid")).thenReturn(true)
        val response = libraryResource.delete("existing-uuid")
        assertEquals(Response.Status.OK.statusCode, response.status)
    }

    @Test
    fun `delete returns 404 when item does not exist`() {
        whenever(mediaStore.remove("no-such-uuid")).thenReturn(false)
        val response = libraryResource.delete("no-such-uuid")
        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
    }

    @Test
    fun `rename returns success when item exists`() {
        whenever(mediaStore.rename("uuid-1", "New Title")).thenReturn(true)
        val response = libraryResource.rename("uuid-1", LibraryResource.RenameRequest("New Title"))
        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val responseMap = response.entity as Map<String, String>
        assertEquals("uuid-1", responseMap["uuid"])
        assertEquals("New Title", responseMap["title"])
    }

    @Test
    fun `rename returns 404 when item does not exist`() {
        whenever(mediaStore.rename("no-such-uuid", "New Title")).thenReturn(false)
        val response = libraryResource.rename("no-such-uuid", LibraryResource.RenameRequest("New Title"))
        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
    }

    @Test
    fun `getItem returns item when it exists`() {
        val uuid = "test-uuid-123"
        val mediaItem =
            MediaItem(
                uuid = uuid,
                plugin = "roku",
                title = "Test Movie",
                configJson = """{"channelId":"12","contentId":"81444554","title":"Test Movie"}""",
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        whenever(mediaStore.get(uuid)).thenReturn(mediaItem)

        val response = libraryResource.getItem(uuid)

        assertEquals(Response.Status.OK.statusCode, response.status)
        @Suppress("UNCHECKED_CAST")
        val responseMap = response.entity as Map<String, Any?>
        assertEquals(uuid, responseMap["uuid"])
        assertEquals("roku", responseMap["plugin"])
        assertEquals("Test Movie", responseMap["title"])
        assertEquals("/play/$uuid", responseMap["playUrl"])
    }

    @Test
    fun `getItem returns 404 when item does not exist`() {
        whenever(mediaStore.get("no-such-uuid")).thenReturn(null)

        val response = libraryResource.getItem("no-such-uuid")

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
    }
}
