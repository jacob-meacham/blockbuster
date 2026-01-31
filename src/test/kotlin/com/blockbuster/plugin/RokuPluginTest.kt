package com.blockbuster.plugin

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.*
import com.blockbuster.media.MediaStore
import com.blockbuster.media.RokuMediaContent
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class RokuPluginTest {

    private lateinit var rokuPlugin: RokuPlugin
    private lateinit var mockMediaStore: MediaStore
    private lateinit var mockHttpClient: OkHttpClient
    private lateinit var mockCall: Call
    private lateinit var mockResponse: Response
    private lateinit var mockChannelPlugin: RokuChannelPlugin

    private val testDeviceIp = "192.168.1.100"
    private val testDeviceName = "Test Roku Device"

    @BeforeEach
    fun setUp() {
        mockMediaStore = mock()
        mockHttpClient = mock()
        mockCall = mock()
        mockResponse = mock()
        mockChannelPlugin = mock()

        // Setup successful HTTP response by default
        whenever(mockHttpClient.newCall(any())).thenReturn(mockCall)
        whenever(mockCall.execute()).thenReturn(mockResponse)
        whenever(mockResponse.isSuccessful).thenReturn(true)
        whenever(mockResponse.body).thenReturn("OK".toResponseBody())
    }

    @Test
    fun `should have correct plugin name and description`() {
        // Given
        rokuPlugin = RokuPlugin(
            deviceIp = testDeviceIp,
            deviceName = testDeviceName,
            mediaStore = mockMediaStore,
            httpClient = mockHttpClient
        )

        // When/Then
        assertEquals("roku", rokuPlugin.getPluginName())
        assertEquals("Controls Roku devices via ECP protocol", rokuPlugin.getDescription())
    }

    @Test
    fun `should delegate to channel plugin for playback`() {
        // Given
        val channelPlugins = mapOf("12" to mockChannelPlugin)
        rokuPlugin = RokuPlugin(
            deviceIp = testDeviceIp,
            deviceName = testDeviceName,
            mediaStore = mockMediaStore,
            httpClient = mockHttpClient,
            channelPlugins = channelPlugins
        )

        val content = RokuMediaContent(
            channelId = "12",
            contentId = "movie123",
            title = "Test Movie"
        )

        val playbackCommand = RokuPlaybackCommand.DeepLink("http://$testDeviceIp:8060/launch/12?contentId=movie123")

        whenever(mockMediaStore.getParsed("uuid123", "roku", RokuMediaContent.Parser)).thenReturn(content)
        whenever(mockChannelPlugin.buildPlaybackCommand(content, testDeviceIp)).thenReturn(playbackCommand)

        // When
        assertDoesNotThrow {
            rokuPlugin.play("uuid123", emptyMap())
        }

        // Then
        verify(mockMediaStore).getParsed("uuid123", "roku", RokuMediaContent.Parser)
        verify(mockChannelPlugin).buildPlaybackCommand(content, testDeviceIp)
        verify(mockHttpClient).newCall(any())
    }

    @Test
    fun `should execute ActionSequence commands`() {
        // Given
        val channelPlugins = mapOf("12" to mockChannelPlugin)
        rokuPlugin = RokuPlugin(
            deviceIp = testDeviceIp,
            deviceName = testDeviceName,
            mediaStore = mockMediaStore,
            httpClient = mockHttpClient,
            channelPlugins = channelPlugins
        )

        val content = RokuMediaContent(
            channelId = "12",
            contentId = "movie123",
            title = "Test Movie"
        )

        val playbackCommand = RokuPlaybackCommand.ActionSequence(
            listOf(
                RokuAction.Launch("12", "contentId=movie123"),
                RokuAction.Wait(1000),
                RokuAction.Press(RokuKey.SELECT, 1)
            )
        )

        whenever(mockMediaStore.getParsed("uuid123", "roku", RokuMediaContent.Parser)).thenReturn(content)
        whenever(mockChannelPlugin.buildPlaybackCommand(content, testDeviceIp)).thenReturn(playbackCommand)

        // When
        assertDoesNotThrow {
            rokuPlugin.play("uuid123", emptyMap())
        }

        // Then
        verify(mockChannelPlugin).buildPlaybackCommand(content, testDeviceIp)
        // Should make 2 HTTP calls: Launch and SELECT press
        verify(mockHttpClient, times(2)).newCall(any())
    }

    @Test
    fun `should throw exception when content not found`() {
        // Given
        rokuPlugin = RokuPlugin(
            deviceIp = testDeviceIp,
            deviceName = testDeviceName,
            mediaStore = mockMediaStore,
            httpClient = mockHttpClient
        )

        whenever(mockMediaStore.getParsed("nonexistent", "roku", RokuMediaContent.Parser)).thenReturn(null)

        // When/Then
        val exception = assertThrows(PluginException::class.java) {
            rokuPlugin.play("nonexistent", emptyMap())
        }

        assertTrue(exception.message!!.contains("Content not found in media store"))
        verify(mockMediaStore).getParsed("nonexistent", "roku", RokuMediaContent.Parser)
    }

    @Test
    fun `should throw exception when no channel plugin registered`() {
        // Given
        rokuPlugin = RokuPlugin(
            deviceIp = testDeviceIp,
            deviceName = testDeviceName,
            mediaStore = mockMediaStore,
            httpClient = mockHttpClient,
            channelPlugins = emptyMap()  // No channel plugins
        )

        val content = RokuMediaContent(
            channelId = "12",
            contentId = "movie123",
            title = "Test Movie"
        )

        whenever(mockMediaStore.getParsed("uuid123", "roku", RokuMediaContent.Parser)).thenReturn(content)

        // When/Then
        val exception = assertThrows(PluginException::class.java) {
            rokuPlugin.play("uuid123", emptyMap())
        }

        assertTrue(exception.message!!.contains("No channel plugin registered for channel ID: 12"))
    }

    @Test
    fun `should aggregate search results from all channel plugins`() {
        // Given
        val mockNetflixPlugin = mock<RokuChannelPlugin>()
        val mockDisneyPlugin = mock<RokuChannelPlugin>()

        val channelPlugins = mapOf(
            "12" to mockNetflixPlugin,
            "291097" to mockDisneyPlugin
        )

        rokuPlugin = RokuPlugin(
            deviceIp = testDeviceIp,
            deviceName = testDeviceName,
            mediaStore = mockMediaStore,
            httpClient = mockHttpClient,
            channelPlugins = channelPlugins
        )

        val netflixResults = listOf(
            RokuMediaContent(channelId = "12", contentId = "123", title = "Netflix Movie")
        )

        val disneyResults = listOf(
            RokuMediaContent(channelId = "291097", contentId = "456", title = "Disney Movie")
        )

        whenever(mockNetflixPlugin.getChannelName()).thenReturn("Netflix")
        whenever(mockDisneyPlugin.getChannelName()).thenReturn("Disney+")
        whenever(mockNetflixPlugin.search("test")).thenReturn(netflixResults)
        whenever(mockDisneyPlugin.search("test")).thenReturn(disneyResults)

        // When
        val results = rokuPlugin.search("test", emptyMap())

        // Then
        assertEquals(2, results.size)
        assertEquals("Netflix Movie", results[0].title)
        assertEquals("Disney Movie", results[1].title)

        verify(mockNetflixPlugin).search("test")
        verify(mockDisneyPlugin).search("test")
    }

    @Test
    fun `should handle empty search results from all channels`() {
        // Given
        val mockChannelPlugin = mock<RokuChannelPlugin>()
        val channelPlugins = mapOf("12" to mockChannelPlugin)

        rokuPlugin = RokuPlugin(
            deviceIp = testDeviceIp,
            deviceName = testDeviceName,
            mediaStore = mockMediaStore,
            httpClient = mockHttpClient,
            channelPlugins = channelPlugins
        )

        whenever(mockChannelPlugin.getChannelName()).thenReturn("Netflix")
        whenever(mockChannelPlugin.search("test")).thenReturn(emptyList())

        // When
        val results = rokuPlugin.search("test", emptyMap())

        // Then
        assertTrue(results.isEmpty())
        verify(mockChannelPlugin).search("test")
    }

    @Test
    fun `should work with no channel plugins registered`() {
        // Given
        rokuPlugin = RokuPlugin(
            deviceIp = testDeviceIp,
            deviceName = testDeviceName,
            mediaStore = mockMediaStore,
            httpClient = mockHttpClient,
            channelPlugins = emptyMap()
        )

        // When
        val results = rokuPlugin.search("test", emptyMap())

        // Then
        assertTrue(results.isEmpty())
    }
}
