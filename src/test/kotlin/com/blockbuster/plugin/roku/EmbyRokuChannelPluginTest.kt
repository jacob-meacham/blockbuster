package com.blockbuster.plugin.roku

import com.blockbuster.media.RokuMediaContent
import com.blockbuster.media.RokuMediaMetadata
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.io.IOException

class EmbyRokuChannelPluginTest {
    private lateinit var httpClient: OkHttpClient
    private lateinit var objectMapper: ObjectMapper
    private lateinit var plugin: EmbyRokuChannelPlugin

    private val embyServerUrl = "http://192.168.1.50:8096"
    private val embyApiKey = "test-api-key"
    private val embyUserId = "test-user-id"

    @BeforeEach
    fun setUp() {
        httpClient = mock()
        objectMapper = ObjectMapper().registerModule(kotlinModule())
        plugin =
            EmbyRokuChannelPlugin(
                embyServerUrl = embyServerUrl,
                embyApiKey = embyApiKey,
                embyUserId = embyUserId,
                httpClient = httpClient,
                objectMapper = objectMapper,
            )
    }

    @Test
    fun `getChannelId should return Emby channel ID`() {
        assertEquals("44191", plugin.getChannelId())
    }

    @Test
    fun `getChannelName should return Emby`() {
        assertEquals("Emby", plugin.getChannelName())
    }

    @Test
    fun `getPublicSearchDomain should return empty string for private server`() {
        assertEquals("", plugin.getPublicSearchDomain())
    }

    @Test
    fun `buildPlaybackCommand should return DeepLink with correct format`() {
        val content =
            RokuMediaContent(
                channelName = "Emby",
                channelId = "44191",
                contentId = "541",
                title = "Test Movie",
                mediaType = "Movie",
            )

        val command = plugin.buildPlaybackCommand(content)

        assertTrue(command is RokuPlaybackCommand.DeepLink)
        val deepLink = command as RokuPlaybackCommand.DeepLink
        assertEquals("44191", deepLink.channelId)
        assertEquals("Command=PlayNow&ItemIds=541", deepLink.params)
    }

    @Test
    fun `buildPlaybackCommand should include StartPositionTicks when resume position is set`() {
        val content =
            RokuMediaContent(
                channelName = "Emby",
                channelId = "44191",
                contentId = "541",
                title = "Test Movie",
                mediaType = "Movie",
                metadata =
                    RokuMediaMetadata(
                        resumePositionTicks = 36000000000L,
                    ),
            )

        val command = plugin.buildPlaybackCommand(content)

        assertTrue(command is RokuPlaybackCommand.DeepLink)
        val deepLink = command as RokuPlaybackCommand.DeepLink
        assertEquals("44191", deepLink.channelId)
        assertTrue(deepLink.params.contains("Command=PlayNow&ItemIds=541"))
        assertTrue(deepLink.params.contains("StartPositionTicks=36000000000"))
    }

    @Test
    fun `buildPlaybackCommand should not include StartPositionTicks when resume position is null`() {
        val content =
            RokuMediaContent(
                channelName = "Emby",
                channelId = "44191",
                contentId = "541",
                title = "Test Movie",
                mediaType = "Movie",
                metadata =
                    RokuMediaMetadata(
                        resumePositionTicks = null,
                    ),
            )

        val command = plugin.buildPlaybackCommand(content)

        assertTrue(command is RokuPlaybackCommand.DeepLink)
        val deepLink = command as RokuPlaybackCommand.DeepLink
        assertFalse(deepLink.params.contains("StartPositionTicks"))
    }

    @Test
    fun `buildPlaybackCommand should not include StartPositionTicks when resume position is zero`() {
        val content =
            RokuMediaContent(
                channelName = "Emby",
                channelId = "44191",
                contentId = "541",
                title = "Test Movie",
                mediaType = "Movie",
                metadata =
                    RokuMediaMetadata(
                        resumePositionTicks = 0L,
                    ),
            )

        val command = plugin.buildPlaybackCommand(content)

        assertTrue(command is RokuPlaybackCommand.DeepLink)
        val deepLink = command as RokuPlaybackCommand.DeepLink
        assertFalse(deepLink.params.contains("StartPositionTicks"))
    }

    @Test
    fun `buildPlaybackCommand should not include StartPositionTicks when metadata is null`() {
        val content =
            RokuMediaContent(
                channelName = "Emby",
                channelId = "44191",
                contentId = "541",
                title = "Test Movie",
                mediaType = "Movie",
                metadata = null,
            )

        val command = plugin.buildPlaybackCommand(content)

        assertTrue(command is RokuPlaybackCommand.DeepLink)
        val deepLink = command as RokuPlaybackCommand.DeepLink
        assertFalse(deepLink.params.contains("StartPositionTicks"))
    }

    @Test
    fun `search should return parsed results on success`() {
        val jsonResponse =
            """
            {
                "Items": [
                    {
                        "Id": "123",
                        "ServerId": "srv1",
                        "Name": "Test Movie",
                        "Type": "Movie",
                        "ProductionYear": 2020
                    }
                ],
                "TotalRecordCount": 1
            }
            """.trimIndent()

        val responseBody = jsonResponse.toResponseBody("application/json".toMediaType())
        val response =
            mock<Response> {
                on { isSuccessful } doReturn true
                on { body } doReturn responseBody
            }
        val call =
            mock<Call> {
                on { execute() } doReturn response
            }
        whenever(httpClient.newCall(any())).thenReturn(call)

        val results = plugin.search("Test Movie")

        assertEquals(1, results.size)
        val result = results[0]
        assertEquals("Emby", result.channelName)
        assertEquals("44191", result.channelId)
        assertEquals("123", result.contentId)
        assertEquals("Test Movie (2020)", result.title)
        assertEquals("Movie", result.mediaType)
        assertNotNull(result.metadata)
        assertEquals("srv1", result.metadata?.serverId)
        assertEquals("Movie", result.metadata?.itemType)
        assertEquals(2020, result.metadata?.year)
    }

    @Test
    fun `search should return parsed results with episode format`() {
        val jsonResponse =
            """
            {
                "Items": [
                    {
                        "Id": "456",
                        "ServerId": "srv1",
                        "Name": "Pilot",
                        "Type": "Episode",
                        "SeriesName": "Breaking Bad",
                        "ParentIndexNumber": 1,
                        "IndexNumber": 1,
                        "ProductionYear": 2008
                    }
                ],
                "TotalRecordCount": 1
            }
            """.trimIndent()

        val responseBody = jsonResponse.toResponseBody("application/json".toMediaType())
        val response =
            mock<Response> {
                on { isSuccessful } doReturn true
                on { body } doReturn responseBody
            }
        val call =
            mock<Call> {
                on { execute() } doReturn response
            }
        whenever(httpClient.newCall(any())).thenReturn(call)

        val results = plugin.search("Breaking Bad")

        assertEquals(1, results.size)
        val result = results[0]
        assertEquals("Breaking Bad - S1E1 - Pilot", result.title)
        assertEquals("Episode", result.mediaType)
        assertEquals("Breaking Bad", result.metadata?.seriesName)
        assertEquals(1, result.metadata?.seasonNumber)
        assertEquals(1, result.metadata?.episodeNumber)
    }

    @Test
    fun `search should throw IOException on HTTP failure`() {
        val response =
            mock<Response> {
                on { isSuccessful } doReturn false
                on { code } doReturn 500
                on { message } doReturn "Internal Server Error"
            }
        val call =
            mock<Call> {
                on { execute() } doReturn response
            }
        whenever(httpClient.newCall(any())).thenReturn(call)

        val exception =
            assertThrows(IOException::class.java) {
                plugin.search("test query")
            }
        assertTrue(exception.message!!.contains("Emby search failed"))
        assertTrue(exception.message!!.contains("500"))
    }

    @Test
    fun `search should throw IOException on network error`() {
        val call =
            mock<Call> {
                on { execute() } doThrow IOException("Connection refused")
            }
        whenever(httpClient.newCall(any())).thenReturn(call)

        val exception =
            assertThrows(IOException::class.java) {
                plugin.search("test query")
            }
        assertEquals("Connection refused", exception.message)
    }

    @Test
    fun `search should build correct request URL and headers`() {
        val jsonResponse = """{"Items": [], "TotalRecordCount": 0}"""
        val responseBody = jsonResponse.toResponseBody("application/json".toMediaType())
        val response =
            mock<Response> {
                on { isSuccessful } doReturn true
                on { body } doReturn responseBody
            }
        val call =
            mock<Call> {
                on { execute() } doReturn response
            }
        whenever(httpClient.newCall(any())).thenReturn(call)

        plugin.search("test query")

        val requestCaptor = argumentCaptor<okhttp3.Request>()
        verify(httpClient).newCall(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        val url = capturedRequest.url.toString()
        assertTrue(url.startsWith("$embyServerUrl/Users/$embyUserId/Items?"))
        assertTrue(
            url.contains("searchTerm=test%20query") || url.contains("searchTerm=test+query") || url.contains("searchTerm=test query"),
        )
        assertTrue(url.contains("recursive=true"))
        assertTrue(url.contains("includeItemTypes=Movie,Episode") || url.contains("includeItemTypes=Movie%2CEpisode"))
        assertEquals(embyApiKey, capturedRequest.header("X-Emby-Token"))
    }

    @Test
    fun `search should populate metadata fields from full Emby response`() {
        val jsonResponse =
            """
            {
                "Items": [
                    {
                        "Id": "789",
                        "ServerId": "srv1",
                        "Name": "Inception",
                        "Type": "Movie",
                        "ProductionYear": 2010,
                        "Overview": "A thief who steals corporate secrets.",
                        "ImageTags": {"Primary": "abc123"},
                        "RunTimeTicks": 88560000000,
                        "UserData": {
                            "PlaybackPositionTicks": 36000000000,
                            "PlayedPercentage": 40.5,
                            "IsFavorite": true,
                            "PlayCount": 2
                        },
                        "CommunityRating": 8.8,
                        "OfficialRating": "PG-13",
                        "Genres": ["Action", "Sci-Fi"]
                    }
                ],
                "TotalRecordCount": 1
            }
            """.trimIndent()

        val responseBody = jsonResponse.toResponseBody("application/json".toMediaType())
        val response =
            mock<Response> {
                on { isSuccessful } doReturn true
                on { body } doReturn responseBody
            }
        val call =
            mock<Call> {
                on { execute() } doReturn response
            }
        whenever(httpClient.newCall(any())).thenReturn(call)

        val results = plugin.search("Inception")

        assertEquals(1, results.size)
        val metadata = results[0].metadata!!
        assertEquals("srv1", metadata.serverId)
        assertEquals("Movie", metadata.itemType)
        assertEquals(2010, metadata.year)
        assertEquals("A thief who steals corporate secrets.", metadata.overview)
        assertEquals("$embyServerUrl/Items/789/Images/Primary?tag=abc123", metadata.imageUrl)
        assertEquals(88560000000L, metadata.runtimeTicks)
        assertEquals(36000000000L, metadata.resumePositionTicks)
        assertEquals(40.5, metadata.playedPercentage)
        assertEquals(true, metadata.isFavorite)
        assertEquals(8.8, metadata.communityRating)
        assertEquals("PG-13", metadata.officialRating)
        assertEquals(listOf("Action", "Sci-Fi"), metadata.genres)
    }

    @Test
    fun `search should handle movie without production year in title`() {
        val jsonResponse =
            """
            {
                "Items": [
                    {
                        "Id": "999",
                        "ServerId": "srv1",
                        "Name": "Unknown Movie",
                        "Type": "Movie"
                    }
                ],
                "TotalRecordCount": 1
            }
            """.trimIndent()

        val responseBody = jsonResponse.toResponseBody("application/json".toMediaType())
        val response =
            mock<Response> {
                on { isSuccessful } doReturn true
                on { body } doReturn responseBody
            }
        val call =
            mock<Call> {
                on { execute() } doReturn response
            }
        whenever(httpClient.newCall(any())).thenReturn(call)

        val results = plugin.search("Unknown")

        assertEquals(1, results.size)
        assertEquals("Unknown Movie", results[0].title)
    }

    @Test
    fun `search should handle empty results`() {
        val jsonResponse = """{"Items": [], "TotalRecordCount": 0}"""
        val responseBody = jsonResponse.toResponseBody("application/json".toMediaType())
        val response =
            mock<Response> {
                on { isSuccessful } doReturn true
                on { body } doReturn responseBody
            }
        val call =
            mock<Call> {
                on { execute() } doReturn response
            }
        whenever(httpClient.newCall(any())).thenReturn(call)

        val results = plugin.search("nonexistent")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `search should throw IOException when response body is null`() {
        val response =
            mock<Response> {
                on { isSuccessful } doReturn true
                on { body } doReturn null
            }
        val call =
            mock<Call> {
                on { execute() } doReturn response
            }
        whenever(httpClient.newCall(any())).thenReturn(call)

        val exception =
            assertThrows(IOException::class.java) {
                plugin.search("test query")
            }
        assertTrue(exception.message!!.contains("Empty response body"))
    }

    @Test
    fun `search should URL-encode query parameters`() {
        val jsonResponse = """{"Items": [], "TotalRecordCount": 0}"""
        val responseBody = jsonResponse.toResponseBody("application/json".toMediaType())
        val response =
            mock<Response> {
                on { isSuccessful } doReturn true
                on { body } doReturn responseBody
            }
        val call =
            mock<Call> {
                on { execute() } doReturn response
            }
        whenever(httpClient.newCall(any())).thenReturn(call)

        plugin.search("test&query=value")

        val requestCaptor = argumentCaptor<okhttp3.Request>()
        verify(httpClient).newCall(requestCaptor.capture())
        val url = requestCaptor.firstValue.url.toString()
        // The & and = should be encoded in the searchTerm parameter
        assertFalse(url.contains("searchTerm=test&query"))
        assertTrue(url.contains("searchTerm=test%26query%3Dvalue") || url.contains("searchTerm=test%26query%3dvalue"))
    }
}
