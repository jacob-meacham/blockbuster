package com.blockbuster.resource

import com.blockbuster.media.RokuMediaContent
import com.blockbuster.media.RokuMediaMetadata
import com.blockbuster.plugin.MediaPlugin
import com.blockbuster.plugin.MediaPluginManager
import com.blockbuster.plugin.SearchOptions
import com.blockbuster.plugin.SearchResult
import com.blockbuster.plugin.roku.RokuChannelPlugin
import com.blockbuster.plugin.roku.RokuPlugin
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class SearchResourceTest {

    private lateinit var pluginManager: MediaPluginManager
    private lateinit var searchResource: SearchResource

    @BeforeEach
    fun setUp() {
        pluginManager = mock()
        searchResource = SearchResource(pluginManager)
    }

    // ---------------------------------------------------------------
    // searchAll tests
    // ---------------------------------------------------------------

    @Test
    fun `searchAll with blank query returns 400 BAD_REQUEST`() {
        val response = searchResource.searchAll(query = "   ", limit = 20, plugin = null)

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        assertEquals("Query parameter 'q' is required", body["error"])
    }

    @Test
    fun `searchAll returns aggregated results from plugins`() {
        // Given
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val content = RokuMediaContent(
            channelName = "Netflix",
            channelId = "12",
            contentId = "81444554",
            title = "Inception",
            mediaType = "movie",
            metadata = RokuMediaMetadata(
                description = "A mind-bending thriller",
                imageUrl = "http://example.com/inception.jpg"
            )
        )
        val searchResult = SearchResult(
            title = "Inception",
            url = null,
            mediaUrl = null,
            content = content
        )
        whenever(mockPlugin.search(eq("inception"), any<SearchOptions>())).thenReturn(listOf(searchResult))
        whenever(pluginManager.getAllPlugins()).thenReturn(listOf(mockPlugin))

        // When
        val response = searchResource.searchAll(query = "inception", limit = 20, plugin = null)

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        assertEquals("inception", body["query"])

        @Suppress("UNCHECKED_CAST")
        val results = body["results"] as List<Map<String, Any?>>

        // Results include the search result plus the manual search tile
        assertTrue(results.size >= 2)

        val firstResult = results[0]
        assertEquals("Inception", firstResult["title"])
        assertEquals("Netflix", firstResult["channelName"])
        assertEquals("12", firstResult["channelId"])
        assertEquals("81444554", firstResult["contentId"])
        assertEquals("movie", firstResult["mediaType"])
        assertEquals("A mind-bending thriller", firstResult["description"])
        assertEquals("http://example.com/inception.jpg", firstResult["imageUrl"])
    }

    @Test
    fun `searchAll with plugin filter only searches specified plugin`() {
        // Given
        val rokuPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(rokuPlugin.getPluginName()).thenReturn("roku")

        val otherPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(otherPlugin.getPluginName()).thenReturn("spotify")

        val content = RokuMediaContent(
            channelName = "Netflix",
            channelId = "12",
            contentId = "12345",
            title = "Test Movie",
            mediaType = "movie"
        )
        whenever(rokuPlugin.search(eq("test"), any<SearchOptions>())).thenReturn(
            listOf(SearchResult(title = "Test Movie", content = content))
        )

        whenever(pluginManager.getPlugin("roku")).thenReturn(rokuPlugin)

        // When
        val response = searchResource.searchAll(query = "test", limit = 20, plugin = "roku")

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)
        verify(pluginManager).getPlugin("roku")
        verify(pluginManager, never()).getAllPlugins()
        verifyNoInteractions(otherPlugin)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val results = body["results"] as List<Map<String, Any?>>

        // Should have the one result plus the manual search tile
        assertEquals(2, results.size)
        assertEquals("Test Movie", results[0]["title"])
        assertEquals("Can't find it?", results[1]["title"])
    }

    @Test
    fun `searchAll deduplication removes duplicate channelId plus contentId`() {
        // Given
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val content1 = RokuMediaContent(
            channelName = "Netflix",
            channelId = "12",
            contentId = "81444554",
            title = "Inception (from Brave)",
            mediaType = "movie",
            metadata = RokuMediaMetadata(searchUrl = "https://netflix.com/title/81444554")
        )
        val content2 = RokuMediaContent(
            channelName = "Netflix",
            channelId = "12",
            contentId = "81444554",
            title = "Inception (from channel)",
            mediaType = "movie"
        )

        whenever(mockPlugin.search(eq("inception"), any<SearchOptions>())).thenReturn(
            listOf(
                SearchResult(title = "Inception (from Brave)", url = "https://netflix.com/title/81444554", content = content1),
                SearchResult(title = "Inception (from channel)", url = null, content = content2)
            )
        )
        whenever(pluginManager.getAllPlugins()).thenReturn(listOf(mockPlugin))

        // When
        val response = searchResource.searchAll(query = "inception", limit = 20, plugin = null)

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val results = body["results"] as List<Map<String, Any?>>

        // Should deduplicate: 1 unique result + 1 manual search tile = 2
        assertEquals(2, results.size)
        assertEquals("Inception (from Brave)", results[0]["title"])
        assertEquals("Can't find it?", results[1]["title"])
    }

    @Test
    fun `searchAll appends manual search tile for roku searches`() {
        // Given
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val content = RokuMediaContent(
            channelName = "Netflix",
            channelId = "12",
            contentId = "12345",
            title = "Some Movie",
            mediaType = "movie"
        )
        whenever(mockPlugin.search(eq("movie"), any<SearchOptions>())).thenReturn(
            listOf(SearchResult(title = "Some Movie", content = content))
        )
        whenever(pluginManager.getAllPlugins()).thenReturn(listOf(mockPlugin))

        // When
        val response = searchResource.searchAll(query = "movie", limit = 20, plugin = null)

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val results = body["results"] as List<Map<String, Any?>>

        val lastResult = results.last()
        assertEquals("manual", lastResult["source"])
        assertEquals("Can't find it?", lastResult["title"])
        assertEquals("Manual Search", lastResult["channelName"])
        assertEquals("MANUAL", lastResult["channelId"])
        assertEquals("MANUAL_SEARCH_TILE", lastResult["contentId"])
        assertEquals("help", lastResult["mediaType"])
        assertEquals("Search manually on your streaming services", lastResult["description"])
    }

    @Test
    fun `searchAll does not append manual search tile when no results`() {
        // Given
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")
        whenever(mockPlugin.search(eq("nonexistent"), any<SearchOptions>())).thenReturn(emptyList())
        whenever(pluginManager.getAllPlugins()).thenReturn(listOf(mockPlugin))

        // When
        val response = searchResource.searchAll(query = "nonexistent", limit = 20, plugin = null)

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val results = body["results"] as List<Map<String, Any?>>

        // No results means no manual search tile either
        assertEquals(0, results.size)
    }

    @Test
    fun `searchAll does not append manual search tile for non-roku plugin filter`() {
        // Given
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("spotify")

        val content = RokuMediaContent(
            channelName = "Spotify",
            channelId = "spotify-1",
            contentId = "track-123",
            title = "Some Song",
            mediaType = "music"
        )
        whenever(mockPlugin.search(eq("song"), any<SearchOptions>())).thenReturn(
            listOf(SearchResult(title = "Some Song", content = content))
        )
        whenever(pluginManager.getPlugin("spotify")).thenReturn(mockPlugin)

        // When
        val response = searchResource.searchAll(query = "song", limit = 20, plugin = "spotify")

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val results = body["results"] as List<Map<String, Any?>>

        // Only the actual result, no manual search tile
        assertEquals(1, results.size)
        assertEquals("Some Song", results[0]["title"])
    }

    @Test
    fun `searchAll handles plugin failure gracefully`() {
        // Given
        val failingPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(failingPlugin.getPluginName()).thenReturn("failing-plugin")
        whenever(failingPlugin.search(any(), any<SearchOptions>())).thenThrow(RuntimeException("Connection refused"))

        val workingPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(workingPlugin.getPluginName()).thenReturn("roku")

        val content = RokuMediaContent(
            channelName = "Netflix",
            channelId = "12",
            contentId = "99999",
            title = "Working Result",
            mediaType = "movie"
        )
        whenever(workingPlugin.search(eq("test"), any<SearchOptions>())).thenReturn(
            listOf(SearchResult(title = "Working Result", content = content))
        )

        whenever(pluginManager.getAllPlugins()).thenReturn(listOf(failingPlugin, workingPlugin))

        // When
        val response = searchResource.searchAll(query = "test", limit = 20, plugin = null)

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val results = body["results"] as List<Map<String, Any?>>

        // The working plugin result should be present, plus manual tile
        assertEquals(2, results.size)
        assertEquals("Working Result", results[0]["title"])
        assertEquals("Can't find it?", results[1]["title"])
    }

    @Test
    fun `searchAll sets source to brave when result has url`() {
        // Given
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val content = RokuMediaContent(
            channelName = "Netflix",
            channelId = "12",
            contentId = "81444554",
            title = "Inception",
            mediaType = "movie",
            metadata = RokuMediaMetadata(searchUrl = "https://netflix.com/title/81444554")
        )
        whenever(mockPlugin.search(eq("inception"), any<SearchOptions>())).thenReturn(
            listOf(SearchResult(title = "Inception", url = "https://netflix.com/title/81444554", content = content))
        )
        whenever(pluginManager.getAllPlugins()).thenReturn(listOf(mockPlugin))

        // When
        val response = searchResource.searchAll(query = "inception", limit = 20, plugin = null)

        // Then
        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val results = body["results"] as List<Map<String, Any?>>

        assertEquals("brave", results[0]["source"])
    }

    @Test
    fun `searchAll sets source to plugin name when result has no url`() {
        // Given
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val content = RokuMediaContent(
            channelName = "Emby",
            channelId = "44191",
            contentId = "541",
            title = "Local Movie",
            mediaType = "movie"
        )
        whenever(mockPlugin.search(eq("local"), any<SearchOptions>())).thenReturn(
            listOf(SearchResult(title = "Local Movie", url = null, content = content))
        )
        whenever(pluginManager.getAllPlugins()).thenReturn(listOf(mockPlugin))

        // When
        val response = searchResource.searchAll(query = "local", limit = 20, plugin = null)

        // Then
        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val results = body["results"] as List<Map<String, Any?>>

        assertEquals("roku", results[0]["source"])
    }

    @Test
    fun `searchAll uses overview as fallback description when description is null`() {
        // Given
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val content = RokuMediaContent(
            channelName = "Emby",
            channelId = "44191",
            contentId = "541",
            title = "Movie With Overview",
            mediaType = "movie",
            metadata = RokuMediaMetadata(
                description = null,
                overview = "This is the overview text"
            )
        )
        whenever(mockPlugin.search(eq("movie"), any<SearchOptions>())).thenReturn(
            listOf(SearchResult(title = "Movie With Overview", content = content))
        )
        whenever(pluginManager.getAllPlugins()).thenReturn(listOf(mockPlugin))

        // When
        val response = searchResource.searchAll(query = "movie", limit = 20, plugin = null)

        // Then
        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val results = body["results"] as List<Map<String, Any?>>

        assertEquals("This is the overview text", results[0]["description"])
    }

    @Test
    fun `searchAll respects limit parameter`() {
        // Given
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val results = (1..10).map { i ->
            SearchResult(
                title = "Movie $i",
                content = RokuMediaContent(
                    channelName = "Netflix",
                    channelId = "12",
                    contentId = "id-$i",
                    title = "Movie $i",
                    mediaType = "movie"
                )
            )
        }
        whenever(mockPlugin.search(eq("movies"), any<SearchOptions>())).thenReturn(results)
        whenever(pluginManager.getAllPlugins()).thenReturn(listOf(mockPlugin))

        // When - limit to 3
        val response = searchResource.searchAll(query = "movies", limit = 3, plugin = null)

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val resultsList = body["results"] as List<Map<String, Any?>>

        // 3 results + 1 manual search tile = 4
        assertEquals(4, resultsList.size)
        // First 3 are actual results
        assertEquals("Movie 1", resultsList[0]["title"])
        assertEquals("Movie 2", resultsList[1]["title"])
        assertEquals("Movie 3", resultsList[2]["title"])
        // Last one is manual search tile
        assertEquals("Can't find it?", resultsList[3]["title"])
    }

    @Test
    fun `searchAll totalResults reflects actual count including manual tile`() {
        // Given
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val content = RokuMediaContent(
            channelName = "Netflix",
            channelId = "12",
            contentId = "123",
            title = "Movie",
            mediaType = "movie"
        )
        whenever(mockPlugin.search(eq("movie"), any<SearchOptions>())).thenReturn(
            listOf(SearchResult(title = "Movie", content = content))
        )
        whenever(pluginManager.getAllPlugins()).thenReturn(listOf(mockPlugin))

        // When
        val response = searchResource.searchAll(query = "movie", limit = 20, plugin = null)

        // Then
        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>

        // 1 result + 1 manual tile = 2
        assertEquals(2, body["totalResults"])
    }

    // ---------------------------------------------------------------
    // search (single plugin) tests
    // ---------------------------------------------------------------

    @Test
    fun `search with blank query returns 400 BAD_REQUEST`() {
        val response = searchResource.search(pluginName = "roku", query = "  ", limit = 10)

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        assertEquals("Query parameter 'q' is required", body["error"])
    }

    @Test
    fun `search for unknown plugin returns 404`() {
        whenever(pluginManager.getPlugin("nonexistent")).thenReturn(null)

        val response = searchResource.search(pluginName = "nonexistent", query = "test", limit = 10)

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        assertEquals("Plugin 'nonexistent' not found", body["error"])
    }

    @Test
    fun `search for known plugin returns results`() {
        // Given
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.getPluginName()).thenReturn("roku")

        val content = RokuMediaContent(
            channelName = "Netflix",
            channelId = "12",
            contentId = "81444554",
            title = "Inception",
            mediaType = "movie"
        )
        val searchResult = SearchResult(title = "Inception", content = content)
        whenever(mockPlugin.search(eq("inception"), any<SearchOptions>())).thenReturn(listOf(searchResult))
        whenever(pluginManager.getPlugin("roku")).thenReturn(mockPlugin)

        // When
        val response = searchResource.search(pluginName = "roku", query = "inception", limit = 10)

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        assertEquals("roku", body["plugin"])
        assertEquals("inception", body["query"])
        assertEquals(1, body["totalResults"])

        @Suppress("UNCHECKED_CAST")
        val results = body["results"] as List<SearchResult<*>>
        assertEquals(1, results.size)
        assertEquals("Inception", results[0].title)
    }

    @Test
    fun `search passes correct SearchOptions to plugin`() {
        // Given
        val mockPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(mockPlugin.search(any(), any<SearchOptions>())).thenReturn(emptyList())
        whenever(pluginManager.getPlugin("roku")).thenReturn(mockPlugin)

        // When
        searchResource.search(pluginName = "roku", query = "test", limit = 5)

        // Then
        verify(mockPlugin).search(eq("test"), eq(SearchOptions(limit = 5)))
    }

    // ---------------------------------------------------------------
    // getAvailablePlugins tests
    // ---------------------------------------------------------------

    @Test
    fun `getAvailablePlugins returns plugin list`() {
        // Given
        val plugin1 = mock<MediaPlugin<RokuMediaContent>>()
        whenever(plugin1.getPluginName()).thenReturn("roku")
        whenever(plugin1.getDescription()).thenReturn("Controls Roku devices via ECP protocol")

        val plugin2 = mock<MediaPlugin<RokuMediaContent>>()
        whenever(plugin2.getPluginName()).thenReturn("spotify")
        whenever(plugin2.getDescription()).thenReturn("Spotify music player")

        whenever(pluginManager.getAllPlugins()).thenReturn(listOf(plugin1, plugin2))

        // When
        val response = searchResource.getAvailablePlugins()

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val plugins = body["plugins"] as List<Map<String, Any?>>

        assertEquals(2, plugins.size)
        assertEquals("roku", plugins[0]["name"])
        assertEquals("Controls Roku devices via ECP protocol", plugins[0]["description"])
        assertEquals("spotify", plugins[1]["name"])
        assertEquals("Spotify music player", plugins[1]["description"])
    }

    @Test
    fun `getAvailablePlugins returns empty list when no plugins registered`() {
        whenever(pluginManager.getAllPlugins()).thenReturn(emptyList())

        val response = searchResource.getAvailablePlugins()

        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val plugins = body["plugins"] as List<Map<String, Any?>>

        assertTrue(plugins.isEmpty())
    }

    // ---------------------------------------------------------------
    // getChannelInfo tests
    // ---------------------------------------------------------------

    @Test
    fun `getChannelInfo returns channel info from RokuPlugin`() {
        // Given
        val channelPlugin1 = mock<RokuChannelPlugin>()
        whenever(channelPlugin1.getChannelId()).thenReturn("12")
        whenever(channelPlugin1.getChannelName()).thenReturn("Netflix")
        whenever(channelPlugin1.getSearchUrl()).thenReturn("https://netflix.com/search")

        val channelPlugin2 = mock<RokuChannelPlugin>()
        whenever(channelPlugin2.getChannelId()).thenReturn("44191")
        whenever(channelPlugin2.getChannelName()).thenReturn("Emby")
        whenever(channelPlugin2.getSearchUrl()).thenReturn("")

        val rokuPlugin = mock<RokuPlugin>()
        whenever(rokuPlugin.getAllChannelPlugins()).thenReturn(listOf(channelPlugin1, channelPlugin2))

        whenever(pluginManager.getPlugin("roku")).thenReturn(rokuPlugin)

        // When
        val response = searchResource.getChannelInfo()

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val channels = body["channels"] as List<Map<String, Any?>>

        assertEquals(2, channels.size)

        assertEquals("12", channels[0]["channelId"])
        assertEquals("Netflix", channels[0]["channelName"])
        assertEquals("https://netflix.com/search", channels[0]["searchUrl"])

        assertEquals("44191", channels[1]["channelId"])
        assertEquals("Emby", channels[1]["channelName"])
        assertEquals("", channels[1]["searchUrl"])
    }

    @Test
    fun `getChannelInfo returns empty list when roku plugin not found`() {
        whenever(pluginManager.getPlugin("roku")).thenReturn(null)

        val response = searchResource.getChannelInfo()

        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val channels = body["channels"] as List<Map<String, Any?>>

        assertTrue(channels.isEmpty())
    }

    @Test
    fun `getChannelInfo returns empty list when plugin is not RokuPlugin type`() {
        // Given - a non-Roku plugin registered as "roku"
        val nonRokuPlugin = mock<MediaPlugin<RokuMediaContent>>()
        whenever(pluginManager.getPlugin("roku")).thenReturn(nonRokuPlugin)

        // When
        val response = searchResource.getChannelInfo()

        // Then
        assertEquals(Response.Status.OK.statusCode, response.status)

        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val channels = body["channels"] as List<Map<String, Any?>>

        assertTrue(channels.isEmpty())
    }
}
