package com.blockbuster.resource

import com.blockbuster.media.RokuMediaContent
import com.blockbuster.media.RokuMediaMetadata
import com.blockbuster.plugin.MediaPlugin
import com.blockbuster.plugin.PluginRegistry
import com.blockbuster.plugin.SearchOptions
import com.blockbuster.plugin.SearchResult
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class SearchResourceTest {
    private lateinit var plugins: MutableMap<String, MediaPlugin<*>>
    private lateinit var searchResource: SearchResource

    @BeforeEach
    fun setUp() {
        plugins = mutableMapOf()
        searchResource = SearchResource(PluginRegistry(plugins))
    }

    // ---------------------------------------------------------------
    // searchAll tests
    // ---------------------------------------------------------------

    @Test
    fun `searchAll with blank query returns 400 BAD_REQUEST`() {
        val response = searchResource.searchAll(query = "   ", limit = 20, plugin = null)

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        val body = response.entity as ErrorResponse
        assertEquals("Query parameter 'q' is required", body.error)
    }

    @Test
    fun `searchAll returns aggregated results from plugins`() {
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val content =
            RokuMediaContent(
                channelName = "Netflix",
                channelId = "12",
                contentId = "81444554",
                title = "Inception",
                mediaType = "movie",
                metadata = RokuMediaMetadata(description = "A mind-bending thriller", imageUrl = "http://example.com/inception.jpg"),
            )
        val searchResult =
            SearchResult(
                title = "Inception",
                url = null,
                content = content,
                source = "brave",
                dedupKey = "12-81444554",
                description = "A mind-bending thriller",
                imageUrl = "http://example.com/inception.jpg",
            )
        whenever(mockPlugin.search(eq("inception"), any<SearchOptions>())).thenReturn(listOf(searchResult))
        plugins["roku"] = mockPlugin

        val response = searchResource.searchAll(query = "inception", limit = 20, plugin = null)

        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as SearchAllResponse
        assertEquals("inception", body.query)
        assertEquals(1, body.totalResults)

        val first = body.results[0]
        assertEquals("Inception", first.title)
        assertEquals("12-81444554", first.dedupKey)
        assertEquals("A mind-bending thriller", first.description)
        assertEquals("http://example.com/inception.jpg", first.imageUrl)
        val resultContent = first.content as RokuMediaContent
        assertEquals("Netflix", resultContent.channelName)
        assertEquals("12", resultContent.channelId)
        assertEquals("81444554", resultContent.contentId)
        assertEquals("movie", resultContent.mediaType)
    }

    @Test
    fun `searchAll with plugin filter only searches specified plugin`() {
        val rokuPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(rokuPlugin.getPluginName()).thenReturn("roku")

        val otherPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(otherPlugin.getPluginName()).thenReturn("spotify")

        val content =
            RokuMediaContent(
                channelName = "Netflix",
                channelId = "12",
                contentId = "12345",
                title = "Test Movie",
                mediaType = "movie",
            )
        whenever(rokuPlugin.search(eq("test"), any<SearchOptions>())).thenReturn(
            listOf(SearchResult(title = "Test Movie", content = content, dedupKey = "12-12345")),
        )

        plugins["roku"] = rokuPlugin
        plugins["spotify"] = otherPlugin

        val response = searchResource.searchAll(query = "test", limit = 20, plugin = "roku")

        assertEquals(Response.Status.OK.statusCode, response.status)
        verifyNoInteractions(otherPlugin)

        val body = response.entity as SearchAllResponse
        assertEquals(1, body.results.size)
        assertEquals("Test Movie", body.results[0].title)
    }

    @Test
    fun `searchAll deduplication removes duplicate dedupKey`() {
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val content1 =
            RokuMediaContent(
                channelName = "Netflix",
                channelId = "12",
                contentId = "81444554",
                title = "Inception (from Brave)",
                mediaType = "movie",
            )
        val content2 =
            RokuMediaContent(
                channelName = "Netflix",
                channelId = "12",
                contentId = "81444554",
                title = "Inception (from channel)",
                mediaType = "movie",
            )

        whenever(mockPlugin.search(eq("inception"), any<SearchOptions>())).thenReturn(
            listOf(
                SearchResult(
                    title = "Inception (from Brave)",
                    url = "https://netflix.com/title/81444554",
                    content = content1,
                    source = "brave",
                    dedupKey = "12-81444554",
                ),
                SearchResult(
                    title = "Inception (from channel)",
                    url = null,
                    content = content2,
                    source = "Emby",
                    dedupKey = "12-81444554",
                ),
            ),
        )
        plugins["roku"] = mockPlugin

        val response = searchResource.searchAll(query = "inception", limit = 20, plugin = null)

        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as SearchAllResponse
        assertEquals(1, body.results.size)
        assertEquals("Inception (from Brave)", body.results[0].title)
    }

    @Test
    fun `searchAll returns empty results when no results found`() {
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")
        whenever(mockPlugin.search(eq("nonexistent"), any<SearchOptions>())).thenReturn(emptyList())
        plugins["roku"] = mockPlugin

        val response = searchResource.searchAll(query = "nonexistent", limit = 20, plugin = null)

        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as SearchAllResponse
        assertEquals(0, body.results.size)
    }

    @Test
    fun `searchAll handles plugin failure gracefully`() {
        val failingPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(failingPlugin.getPluginName()).thenReturn("failing-plugin")
        whenever(failingPlugin.search(any(), any<SearchOptions>())).thenThrow(RuntimeException("Connection refused"))

        val workingPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(workingPlugin.getPluginName()).thenReturn("roku")

        val content =
            RokuMediaContent(
                channelName = "Netflix",
                channelId = "12",
                contentId = "99999",
                title = "Working Result",
                mediaType = "movie",
            )
        whenever(workingPlugin.search(eq("test"), any<SearchOptions>())).thenReturn(
            listOf(SearchResult(title = "Working Result", content = content, dedupKey = "12-99999")),
        )

        plugins["failing-plugin"] = failingPlugin
        plugins["roku"] = workingPlugin

        val response = searchResource.searchAll(query = "test", limit = 20, plugin = null)

        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as SearchAllResponse
        assertEquals(1, body.results.size)
        assertEquals("Working Result", body.results[0].title)
    }

    @Test
    fun `searchAll passes source from SearchResult display fields`() {
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val content =
            RokuMediaContent(
                channelName = "Netflix",
                channelId = "12",
                contentId = "81444554",
                title = "Inception",
                mediaType = "movie",
            )
        whenever(mockPlugin.search(eq("inception"), any<SearchOptions>())).thenReturn(
            listOf(
                SearchResult(
                    title = "Inception",
                    url = "https://netflix.com/title/81444554",
                    content = content,
                    source = "brave",
                    dedupKey = "12-81444554",
                ),
            ),
        )
        plugins["roku"] = mockPlugin

        val response = searchResource.searchAll(query = "inception", limit = 20, plugin = null)
        val body = response.entity as SearchAllResponse
        assertEquals("brave", body.results[0].source)
    }

    @Test
    fun `searchAll passes plugin-provided source when no url`() {
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val content =
            RokuMediaContent(
                channelName = "Emby",
                channelId = "44191",
                contentId = "541",
                title = "Local Movie",
                mediaType = "movie",
            )
        whenever(mockPlugin.search(eq("local"), any<SearchOptions>())).thenReturn(
            listOf(
                SearchResult(title = "Local Movie", url = null, content = content, source = "Emby", dedupKey = "44191-541"),
            ),
        )
        plugins["roku"] = mockPlugin

        val response = searchResource.searchAll(query = "local", limit = 20, plugin = null)
        val body = response.entity as SearchAllResponse
        assertEquals("Emby", body.results[0].source)
    }

    @Test
    fun `searchAll uses description from SearchResult display fields`() {
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val content =
            RokuMediaContent(
                channelName = "Emby",
                channelId = "44191",
                contentId = "541",
                title = "Movie With Overview",
                mediaType = "movie",
                metadata = RokuMediaMetadata(description = null, overview = "This is the overview text"),
            )
        whenever(mockPlugin.search(eq("movie"), any<SearchOptions>())).thenReturn(
            listOf(
                SearchResult(
                    title = "Movie With Overview",
                    content = content,
                    description = "This is the overview text",
                    dedupKey = "44191-541",
                ),
            ),
        )
        plugins["roku"] = mockPlugin

        val response = searchResource.searchAll(query = "movie", limit = 20, plugin = null)
        val body = response.entity as SearchAllResponse
        assertEquals("This is the overview text", body.results[0].description)
    }

    @Test
    fun `searchAll respects limit parameter`() {
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val results =
            (1..10).map { i ->
                SearchResult(
                    title = "Movie $i",
                    content =
                        RokuMediaContent(
                            channelName = "Netflix",
                            channelId = "12",
                            contentId = "id-$i",
                            title = "Movie $i",
                            mediaType = "movie",
                        ),
                    dedupKey = "12-id-$i",
                )
            }
        whenever(mockPlugin.search(eq("movies"), any<SearchOptions>())).thenReturn(results)
        plugins["roku"] = mockPlugin

        val response = searchResource.searchAll(query = "movies", limit = 3, plugin = null)

        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as SearchAllResponse
        assertEquals(3, body.results.size)
        assertEquals("Movie 1", body.results[0].title)
        assertEquals("Movie 2", body.results[1].title)
        assertEquals("Movie 3", body.results[2].title)
    }

    @Test
    fun `searchAll totalResults reflects actual count`() {
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val content =
            RokuMediaContent(
                channelName = "Netflix",
                channelId = "12",
                contentId = "123",
                title = "Movie",
                mediaType = "movie",
            )
        whenever(mockPlugin.search(eq("movie"), any<SearchOptions>())).thenReturn(
            listOf(SearchResult(title = "Movie", content = content, dedupKey = "12-123")),
        )
        plugins["roku"] = mockPlugin

        val response = searchResource.searchAll(query = "movie", limit = 20, plugin = null)
        val body = response.entity as SearchAllResponse
        assertEquals(1, body.totalResults)
    }

    // ---------------------------------------------------------------
    // search (single plugin) tests
    // ---------------------------------------------------------------

    @Test
    fun `search with blank query returns 400 BAD_REQUEST`() {
        val response = searchResource.search(pluginName = "roku", query = "  ", limit = 10)

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        val body = response.entity as ErrorResponse
        assertEquals("Query parameter 'q' is required", body.error)
    }

    @Test
    fun `search for unknown plugin returns 404`() {
        val response = searchResource.search(pluginName = "nonexistent", query = "test", limit = 10)

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
        val body = response.entity as ErrorResponse
        assertEquals("Plugin 'nonexistent' not found", body.error)
    }

    @Test
    fun `search for known plugin returns results`() {
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val content =
            RokuMediaContent(
                channelName = "Netflix",
                channelId = "12",
                contentId = "81444554",
                title = "Inception",
                mediaType = "movie",
            )
        val searchResult = SearchResult(title = "Inception", content = content)
        whenever(mockPlugin.search(eq("inception"), any<SearchOptions>())).thenReturn(listOf(searchResult))
        plugins["roku"] = mockPlugin

        val response = searchResource.search(pluginName = "roku", query = "inception", limit = 10)

        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as SearchPluginResponse
        assertEquals("roku", body.plugin)
        assertEquals("inception", body.query)
        assertEquals(1, body.totalResults)
        assertEquals("Inception", body.results[0].title)
    }

    @Test
    fun `search passes correct SearchOptions to plugin`() {
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.search(any(), any<SearchOptions>())).thenReturn(emptyList())
        plugins["roku"] = mockPlugin

        searchResource.search(pluginName = "roku", query = "test", limit = 5)

        verify(mockPlugin).search(eq("test"), eq(SearchOptions(limit = 5)))
    }

    // ---------------------------------------------------------------
    // getAvailablePlugins tests
    // ---------------------------------------------------------------

    @Test
    fun `getAvailablePlugins returns plugin list`() {
        val plugin1 = mock<MediaPlugin<RokuMediaContent>>()
        whenever(plugin1.getPluginName()).thenReturn("roku")
        whenever(plugin1.getDescription()).thenReturn("Controls Roku devices via ECP protocol")

        val plugin2 = mock<MediaPlugin<RokuMediaContent>>()
        whenever(plugin2.getPluginName()).thenReturn("spotify")
        whenever(plugin2.getDescription()).thenReturn("Spotify music player")

        plugins["roku"] = plugin1
        plugins["spotify"] = plugin2

        val response = searchResource.getAvailablePlugins()

        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as PluginListResponse
        assertEquals(2, body.plugins.size)
    }

    @Test
    fun `getAvailablePlugins returns empty list when no plugins registered`() {
        val response = searchResource.getAvailablePlugins()

        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as PluginListResponse
        assertTrue(body.plugins.isEmpty())
    }
}
