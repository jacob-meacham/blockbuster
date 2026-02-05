package com.blockbuster.search

import com.blockbuster.media.RokuMediaContent
import com.blockbuster.plugin.roku.PrimeVideoRokuChannelPlugin
import com.blockbuster.plugin.roku.RokuChannelPlugin
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class BraveStreamingSearchProviderTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var httpClient: OkHttpClient

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        httpClient = OkHttpClient()
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    @Test
    fun `searchStreaming returns empty list when API key is blank`() {
        val channelPlugin: RokuChannelPlugin = mock()
        val provider =
            BraveStreamingSearchProvider(
                apiKey = "",
                httpClient = httpClient,
                channelPlugins = listOf(channelPlugin),
            )

        val results = provider.searchStreaming("the matrix")

        assertEquals(emptyList<RokuMediaContent>(), results)
        verifyNoInteractions(channelPlugin)
    }

    @Test
    fun `searchStreaming throws BraveSearchException on HTTP error`() {
        mockServer.enqueue(MockResponse().setResponseCode(401).setBody("""{"error": "unauthorized"}"""))

        val channelPlugin: RokuChannelPlugin =
            mock {
                on { getPublicSearchDomain() } doReturn "netflix.com"
                on { getChannelName() } doReturn "Netflix"
            }

        val provider = createProviderWithMockServer(listOf(channelPlugin))

        val exception =
            assertThrows(BraveSearchException::class.java) {
                provider.searchStreaming("test query")
            }

        assertEquals(401, exception.statusCode)
        assertTrue(exception.message!!.contains("401"))
    }

    @Test
    fun `searchStreaming throws BraveSearchException on empty response body`() {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val channelPlugin: RokuChannelPlugin =
            mock {
                on { getPublicSearchDomain() } doReturn "netflix.com"
                on { getChannelName() } doReturn "Netflix"
            }

        val provider = createProviderWithMockServer(listOf(channelPlugin))

        assertThrows(BraveSearchException::class.java) {
            provider.searchStreaming("test query")
        }
    }

    @Test
    fun `searchStreaming extracts content from matching channel plugin`() {
        val searchResponse =
            """
            {
              "web": {
                "results": [
                  {
                    "title": "The Matrix on Netflix",
                    "url": "https://www.netflix.com/title/20557937",
                    "description": "A sci-fi classic",
                    "thumbnail": { "src": "https://example.com/matrix.jpg" }
                  }
                ]
              }
            }
            """.trimIndent()

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(searchResponse))

        val extractedContent =
            RokuMediaContent(
                channelId = "12",
                channelName = "Netflix",
                contentId = "20557937",
                title = "The Matrix",
            )

        val channelPlugin: RokuChannelPlugin =
            mock {
                on { getPublicSearchDomain() } doReturn "netflix.com"
                on { getChannelName() } doReturn "Netflix"
                on { extractFromUrl(any(), anyOrNull(), anyOrNull()) } doReturn extractedContent
            }

        val provider = createProviderWithMockServer(listOf(channelPlugin))

        val results = provider.searchStreaming("the matrix")

        assertEquals(1, results.size)
        assertEquals("12", results[0].channelId)
        assertEquals("The Matrix on Netflix", results[0].title)
        assertEquals("A sci-fi classic", results[0].metadata?.description)
        assertEquals("https://example.com/matrix.jpg", results[0].metadata?.imageUrl)
        assertEquals("https://www.netflix.com/title/20557937", results[0].metadata?.originalUrl)
    }

    @Test
    fun `searchStreaming skips Amazon results without Prime Video in title`() {
        val searchResponse =
            """
            {
              "web": {
                "results": [
                  {
                    "title": "The Matrix DVD on Amazon",
                    "url": "https://www.amazon.com/dp/B00004CX8H",
                    "description": "Buy the DVD"
                  },
                  {
                    "title": "The Matrix | Prime Video",
                    "url": "https://www.amazon.com/gp/video/detail/B00BI3RKPE",
                    "description": "Watch on Prime"
                  }
                ]
              }
            }
            """.trimIndent()

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(searchResponse))

        // Use real plugin - filtering logic is now in PrimeVideoRokuChannelPlugin
        val provider = createProviderWithMockServer(listOf(PrimeVideoRokuChannelPlugin()))

        val results = provider.searchStreaming("the matrix")

        // Only the Prime Video result should be included (plugin filters by title)
        assertEquals(1, results.size)
        assertEquals("B00BI3RKPE", results[0].contentId)
    }

    @Test
    fun `searchStreaming skips results not matching any channel plugin`() {
        val searchResponse =
            """
            {
              "web": {
                "results": [
                  {
                    "title": "The Matrix on Random Site",
                    "url": "https://www.randomsite.com/movie/12345",
                    "description": "Not a real streaming service"
                  }
                ]
              }
            }
            """.trimIndent()

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(searchResponse))

        val channelPlugin: RokuChannelPlugin =
            mock {
                on { getPublicSearchDomain() } doReturn "netflix.com"
                on { getChannelName() } doReturn "Netflix"
                on { extractFromUrl(any(), anyOrNull(), anyOrNull()) } doReturn null
            }

        val provider = createProviderWithMockServer(listOf(channelPlugin))

        val results = provider.searchStreaming("the matrix")

        assertEquals(0, results.size)
    }

    @Test
    fun `searchStreaming deduplicates results by channelId and contentId`() {
        val searchResponse =
            """
            {
              "web": {
                "results": [
                  {
                    "title": "The Matrix - Netflix",
                    "url": "https://www.netflix.com/title/20557937",
                    "description": "Result 1"
                  },
                  {
                    "title": "The Matrix (1999) - Netflix",
                    "url": "https://www.netflix.com/watch/20557937",
                    "description": "Result 2"
                  }
                ]
              }
            }
            """.trimIndent()

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(searchResponse))

        val content =
            RokuMediaContent(
                channelId = "12",
                channelName = "Netflix",
                contentId = "20557937",
                title = "The Matrix",
            )

        val channelPlugin: RokuChannelPlugin =
            mock {
                on { getPublicSearchDomain() } doReturn "netflix.com"
                on { getChannelName() } doReturn "Netflix"
                on { extractFromUrl(any(), anyOrNull(), anyOrNull()) } doReturn content
            }

        val provider = createProviderWithMockServer(listOf(channelPlugin))

        val results = provider.searchStreaming("the matrix")

        assertEquals(1, results.size)
    }

    @Test
    fun `searchStreaming builds correct site query with multiple channels`() {
        mockServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"web": {"results": []}}"""),
        )

        val netflixPlugin: RokuChannelPlugin =
            mock {
                on { getPublicSearchDomain() } doReturn "netflix.com"
                on { getChannelName() } doReturn "Netflix"
            }

        val disneyPlugin: RokuChannelPlugin =
            mock {
                on { getPublicSearchDomain() } doReturn "disneyplus.com"
                on { getChannelName() } doReturn "Disney+"
            }

        val embyPlugin: RokuChannelPlugin =
            mock {
                on { getPublicSearchDomain() } doReturn ""
                on { getChannelName() } doReturn "Emby"
            }

        val provider = createProviderWithMockServer(listOf(netflixPlugin, disneyPlugin, embyPlugin))
        provider.searchStreaming("test")

        val request = mockServer.takeRequest()
        val requestUrl = request.requestUrl!!.toString()
        // Should include Netflix and Disney but NOT Emby (blank domain)
        assertTrue(requestUrl.contains("site%3Anetflix.com"))
        assertTrue(requestUrl.contains("site%3Adisneyplus.com"))
        assertTrue(!requestUrl.contains("emby"))
    }

    @Test
    fun `searchStreaming sends correct API headers`() {
        mockServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"web": {"results": []}}"""),
        )

        val channelPlugin: RokuChannelPlugin =
            mock {
                on { getPublicSearchDomain() } doReturn "netflix.com"
                on { getChannelName() } doReturn "Netflix"
            }

        val provider = createProviderWithMockServer(listOf(channelPlugin))
        provider.searchStreaming("test")

        val request = mockServer.takeRequest()
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals("test-api-key", request.getHeader("X-Subscription-Token"))
    }

    @Test
    fun `searchStreaming handles null web results`() {
        mockServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"web": null}"""),
        )

        val channelPlugin: RokuChannelPlugin =
            mock {
                on { getPublicSearchDomain() } doReturn "netflix.com"
                on { getChannelName() } doReturn "Netflix"
            }

        val provider = createProviderWithMockServer(listOf(channelPlugin))
        val results = provider.searchStreaming("test")

        assertEquals(0, results.size)
    }

    private fun createProviderWithMockServer(channelPlugins: List<RokuChannelPlugin>): BraveStreamingSearchProvider {
        // Use reflection or create a subclass to override the URL
        // Since we can't easily override the URL, we use MockWebServer with a custom OkHttpClient interceptor
        val interceptingClient =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val originalRequest = chain.request()
                    val newUrl =
                        originalRequest.url.newBuilder()
                            .scheme("http")
                            .host(mockServer.hostName)
                            .port(mockServer.port)
                            .build()
                    chain.proceed(originalRequest.newBuilder().url(newUrl).build())
                }
                .build()

        return BraveStreamingSearchProvider(
            apiKey = "test-api-key",
            httpClient = interceptingClient,
            channelPlugins = channelPlugins,
        )
    }
}
