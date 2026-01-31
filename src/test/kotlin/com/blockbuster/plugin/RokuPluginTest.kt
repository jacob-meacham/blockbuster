package com.blockbuster.plugin

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import com.blockbuster.media.MediaStore
import com.blockbuster.media.RokuMediaContent
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RokuPluginTest {
    
    private lateinit var rokuPlugin: RokuPlugin
    private val testDeviceIp = "192.168.1.100"
    private val testDeviceName = "Test Roku Device"
    
    @Mock
    private lateinit var mockMediaStore: MediaStore
    
    @Mock
    private lateinit var mockHttpClient: OkHttpClient
    
    @Mock
    private lateinit var mockCall: Call
    
    @Mock
    private lateinit var mockResponse: Response
    
    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        rokuPlugin = RokuPlugin(
            deviceIp = testDeviceIp,
            deviceName = testDeviceName,
            mediaStore = mockMediaStore,
            httpClient = mockHttpClient
        )
    }
    
    /**
     * Helper method to set up common mock behavior for successful HTTP requests
     */
    private fun setupSuccessfulHttpResponse() {
        whenever(mockHttpClient.newCall(any())).thenReturn(mockCall)
        whenever(mockCall.execute()).thenReturn(mockResponse)
        whenever(mockResponse.isSuccessful).thenReturn(true)
        whenever(mockResponse.body).thenReturn("OK".toResponseBody())
    }
    
    /**
     * Helper method to set up common mock behavior for failed HTTP requests
     */
    private fun setupFailedHttpResponse(responseCode: Int) {
        whenever(mockHttpClient.newCall(any())).thenReturn(mockCall)
        whenever(mockCall.execute()).thenReturn(mockResponse)
        whenever(mockResponse.isSuccessful).thenReturn(false)
        whenever(mockResponse.code).thenReturn(responseCode)
    }
    
    @Test
    fun `should have correct plugin name and description`() {
        // When/Then
        assertEquals("roku", rokuPlugin.getPluginName())
        assertEquals("Controls Roku devices via ECP protocol", rokuPlugin.getDescription())
    }
    
    @Test
    fun `should execute play command successfully`() {
        // Given
        val uuid = "myMovie123"
        val mockContent = RokuMediaContent(
            channelId = "netflix",
            contentId = "movie123",
            mediaType = "movie",
            title = "The Matrix"
        )
        
        whenever(mockMediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)).thenReturn(mockContent)
        setupSuccessfulHttpResponse()
        
        // When/Then
        assertDoesNotThrow {
            rokuPlugin.play(uuid, emptyMap())
        }
        
        verify(mockMediaStore).getParsed(uuid, "roku", RokuMediaContent.Parser)
        verify(mockHttpClient).newCall(any())
        verify(mockCall).execute()
    }
    
    @Test
    fun `should execute play command with different content`() {
        // Given
        val uuid = "myShow456"
        val mockContent = RokuMediaContent(
            channelId = "hbo",
            contentId = "show456",
            mediaType = "show",
            title = "Game of Thrones"
        )
        
        whenever(mockMediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)).thenReturn(mockContent)
        setupSuccessfulHttpResponse()
        
        // When/Then
        assertDoesNotThrow {
            rokuPlugin.play(uuid, emptyMap())
        }
        
        verify(mockMediaStore).getParsed(uuid, "roku", RokuMediaContent.Parser)
        verify(mockHttpClient).newCall(any())
        verify(mockCall).execute()
    }
    
    @Test
    fun `should handle content with minimal required fields`() {
        // Given
        val uuid = "basicContent"
        val mockContent = RokuMediaContent(
            channelId = "basic-channel",
            contentId = "basic-content"
        )
        
        whenever(mockMediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)).thenReturn(mockContent)
        setupSuccessfulHttpResponse()
        
        // When/Then
        assertDoesNotThrow {
            rokuPlugin.play(uuid, emptyMap())
        }
        
        verify(mockMediaStore).getParsed(uuid, "roku", RokuMediaContent.Parser)
        verify(mockHttpClient).newCall(any())
        verify(mockCall).execute()
    }
    
    @Test
    fun `should handle content with special characters`() {
        // Given
        val uuid = "specialMovie"
        val mockContent = RokuMediaContent(
            channelId = "netflix",
            contentId = "movie-123",
            mediaType = "movie",
            title = "The Matrix: Reloaded (2003)"
        )
        
        whenever(mockMediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)).thenReturn(mockContent)
        setupSuccessfulHttpResponse()
        
        // When/Then
        assertDoesNotThrow {
            rokuPlugin.play(uuid, emptyMap())
        }
        
        verify(mockMediaStore).getParsed(uuid, "roku", RokuMediaContent.Parser)
        verify(mockHttpClient).newCall(any())
        verify(mockCall).execute()
    }
    
    @Test
    fun `should handle different ecpCommand values`() {
        // Given
        val uuid = "installApp"
        val mockContent = RokuMediaContent(
            channelId = "netflix",
            contentId = "app123",
            ecpCommand = "install",
            title = "Netflix App"
        )
        
        whenever(mockMediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)).thenReturn(mockContent)
        setupSuccessfulHttpResponse()
        
        // When/Then
        assertDoesNotThrow {
            rokuPlugin.play(uuid, emptyMap())
        }
        
        verify(mockMediaStore).getParsed(uuid, "roku", RokuMediaContent.Parser)
        verify(mockHttpClient).newCall(any())
        verify(mockCall).execute()
    }
    
    @Test
    fun `should handle null optional fields gracefully`() {
        // Given
        val uuid = "minimalContent"
        val mockContent = RokuMediaContent(
            channelId = "netflix",
            contentId = "movie123"
        )
        
        whenever(mockMediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)).thenReturn(mockContent)
        setupSuccessfulHttpResponse()
        
        // When/Then
        assertDoesNotThrow {
            rokuPlugin.play(uuid, emptyMap())
        }
        
        verify(mockMediaStore).getParsed(uuid, "roku", RokuMediaContent.Parser)
        verify(mockHttpClient).newCall(any())
        verify(mockCall).execute()
    }
    
    @Test
    fun `should handle empty string optional fields gracefully`() {
        // Given
        val uuid = "emptyFields"
        val mockContent = RokuMediaContent(
            channelId = "netflix",
            contentId = "movie123",
            mediaType = "",
            title = ""
        )
        
        whenever(mockMediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)).thenReturn(mockContent)
        setupSuccessfulHttpResponse()
        
        // When/Then
        assertDoesNotThrow {
            rokuPlugin.play(uuid, emptyMap())
        }
        
        verify(mockMediaStore).getParsed(uuid, "roku", RokuMediaContent.Parser)
        verify(mockHttpClient).newCall(any())
        verify(mockCall).execute()
    }
    
    @Test
    fun `should handle multiple content types`() {
        // Given
        val testCases = listOf(
            Triple("myMovie123", "netflix", "movie"),
            Triple("myShow456", "hbo", "show"),
            Triple("myVideo789", "youtube", "video")
        )
        
        // When/Then
        testCases.forEach { (uuid, channelId, mediaType) ->
            val mockContent = RokuMediaContent(
                channelId = channelId,
                contentId = "content123",
                mediaType = mediaType,
                title = "Test Content"
            )
            
            // Reset mocks for each iteration
            reset(mockMediaStore, mockHttpClient, mockCall, mockResponse)
            
            whenever(mockMediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)).thenReturn(mockContent)
            setupSuccessfulHttpResponse()
            
            assertDoesNotThrow {
                rokuPlugin.play(uuid, emptyMap())
            }
            
            verify(mockMediaStore).getParsed(uuid, "roku", RokuMediaContent.Parser)
            verify(mockHttpClient).newCall(any())
            verify(mockCall).execute()
        }
    }
    
    @Test
    fun `should handle different channel types`() {
        // Given
        val testCases = listOf(
            "myNetflixMovie" to "netflix",
            "myHboShow" to "hbo",
            "myYoutubeVideo" to "youtube",
            "myPlexContent" to "plex"
        )
        
        // When/Then
        testCases.forEach { (uuid, channelId) ->
            val mockContent = RokuMediaContent(
                channelId = channelId,
                contentId = "content123",
                title = "Test Content"
            )
            
            // Reset mocks for each iteration
            reset(mockMediaStore, mockHttpClient, mockCall, mockResponse)
            
            whenever(mockMediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)).thenReturn(mockContent)
            setupSuccessfulHttpResponse()
            
            assertDoesNotThrow {
                rokuPlugin.play(uuid, emptyMap())
            }
            
            verify(mockMediaStore).getParsed(uuid, "roku", RokuMediaContent.Parser)
            verify(mockHttpClient).newCall(any())
            verify(mockCall).execute()
        }
    }
    
    @Test
    fun `should throw exception when content not found in media store`() {
        // Given
        val uuid = "nonExistentContent"
        whenever(mockMediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)).thenReturn(null)
        
        // When/Then
        val exception = assertThrows<PluginException> {
            rokuPlugin.play(uuid, emptyMap())
        }
        
        assertEquals("Failed to execute Roku command: Content not found in media store", exception.message)
        verify(mockMediaStore).getParsed(uuid, "roku", RokuMediaContent.Parser)
        verify(mockHttpClient, never()).newCall(any())
    }
    
    @Test
    fun `should throw exception when HTTP request fails`() {
        // Given
        val uuid = "myMovie123"
        val mockContent = RokuMediaContent(
            channelId = "netflix",
            contentId = "movie123",
            mediaType = "movie",
            title = "The Matrix"
        )
        
        whenever(mockMediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)).thenReturn(mockContent)
        setupFailedHttpResponse(404)
        
        // When/Then
        val exception = assertThrows<PluginException> {
            rokuPlugin.play(uuid, emptyMap())
        }
        
        assertEquals("Failed to execute Roku command: Failed to send ECP request: ECP request failed with response code: 404", exception.message)
        verify(mockMediaStore).getParsed(uuid, "roku", RokuMediaContent.Parser)
        verify(mockHttpClient).newCall(any())
        verify(mockCall).execute()
    }
}
