package com.blockbuster.resource

import com.blockbuster.plugin.MediaPluginManager
import com.blockbuster.plugin.PluginException
import com.blockbuster.plugin.SearchOptions
import com.blockbuster.media.RokuMediaContent
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/search")
@Produces(MediaType.APPLICATION_JSON)
class SearchResource(
    private val pluginManager: MediaPluginManager
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Serve the SPA index page for browser navigation to /search.
     * This prevents a 404 when the browser requests /search directly
     * (e.g., page refresh) since this class owns the /search path.
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    fun getSearchPage(): Response {
        val inputStream = javaClass.classLoader.getResourceAsStream("assets/index.html")
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity("<h1>404 - Page not found</h1>")
                .build()
        val content = inputStream.bufferedReader().use { it.readText() }
        return Response.ok(content, MediaType.TEXT_HTML).build()
    }

    /**
     * Aggregated search across all sources.
     * Delegates to plugins which may include Brave Search, channel-specific APIs, etc.
     * Returns unified results with channel information.
     */
    @GET
    @Path("/all")
    fun searchAll(
        @QueryParam("q") query: String,
        @QueryParam("limit") @DefaultValue("20") limit: Int,
        @QueryParam("plugin") plugin: String? = null
    ): Response {
        return try {
            if (query.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(mapOf("error" to "Query parameter 'q' is required"))
                    .build()
            }

            logger.info("Aggregated search for query '$query'")

            val pluginsToSearch = if (plugin != null) {
                listOfNotNull(pluginManager.getPlugin(plugin))
            } else {
                pluginManager.getAllPlugins()
            }

            val allResults = collectSearchResults(pluginsToSearch, query, limit)

            val dedupResults = allResults
                .distinctBy { "${it["channelId"]}-${it["contentId"]}" }
                .take(limit)

            val results = appendManualSearchTile(dedupResults, plugin)

            Response.ok(mapOf(
                "query" to query,
                "totalResults" to results.size,
                "results" to results
            )).build()

        } catch (e: Exception) {
            logger.error("Aggregated search failed: ${e.message}", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Internal server error"))
                .build()
        }
    }

    private fun collectSearchResults(
        plugins: List<com.blockbuster.plugin.MediaPlugin<*>>,
        query: String,
        limit: Int
    ): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()

        plugins.forEach { searchPlugin ->
            try {
                val pluginResults = searchPlugin.search(query, SearchOptions(limit = limit))
                pluginResults.forEach { result ->
                    val content = result.content
                    if (content is RokuMediaContent) {
                        results.add(buildResultMap(content, result, searchPlugin.getPluginName()))
                    }
                }
                logger.info("Plugin '${searchPlugin.getPluginName()}' returned ${pluginResults.size} results")
            } catch (e: Exception) {
                logger.warn("Plugin '${searchPlugin.getPluginName()}' search failed: ${e.message}")
            }
        }

        return results
    }

    private fun buildResultMap(
        content: RokuMediaContent,
        result: com.blockbuster.plugin.SearchResult<*>,
        pluginName: String
    ): Map<String, Any?> {
        val description = content.metadata?.description ?: content.metadata?.overview ?: ""
        val source = if (result.url != null && result.url.toString().isNotBlank()) "brave" else pluginName

        return mapOf(
            "source" to source,
            "plugin" to pluginName,
            "title" to result.title,
            "channelName" to content.channelName,
            "channelId" to content.channelId,
            "contentId" to content.contentId,
            "mediaType" to content.mediaType,
            "url" to result.url,
            "description" to description,
            "imageUrl" to (content.metadata?.imageUrl ?: ""),
            "content" to content
        )
    }

    private fun appendManualSearchTile(
        results: List<Map<String, Any?>>,
        pluginFilter: String?
    ): List<Map<String, Any?>> {
        val isRokuSearch = pluginFilter == null || pluginFilter == "roku"
        if (results.isEmpty() || !isRokuSearch) return results

        return results + mapOf(
            "source" to "manual",
            "title" to "Can't find it?",
            "channelName" to "Manual Search",
            "channelId" to "MANUAL",
            "contentId" to "MANUAL_SEARCH_TILE",
            "mediaType" to "help",
            "description" to "Search manually on your streaming services",
            "content" to mapOf("type" to "manual_search_instructions")
        )
    }

    @GET
    @Path("/{pluginName}")
    fun search(
        @PathParam("pluginName") pluginName: String,
        @QueryParam("q") query: String,
        @QueryParam("limit") @DefaultValue("10") limit: Int
    ): Response {
        return try {
            if (query.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(mapOf("error" to "Query parameter 'q' is required"))
                    .build()
            }

            val plugin = pluginManager.getPlugin(pluginName)
                ?: return Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Plugin '$pluginName' not found"))
                    .build()

            logger.info("Searching plugin '$pluginName' for query '$query'")

            val results = plugin.search(query, SearchOptions(limit = limit))

            val response = mapOf(
                "plugin" to pluginName,
                "query" to query,
                "totalResults" to results.size,
                "results" to results
            )

            Response.ok(response).build()

        } catch (e: PluginException) {
            logger.error("Plugin search failed: ${e.message}", e)
            Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to e.message))
                .build()
        } catch (e: Exception) {
            logger.error("Search request failed: ${e.message}", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Internal server error"))
                .build()
        }
    }

    @GET
    @Path("/plugins")
    fun getAvailablePlugins(): Response {
        return try {
            val plugins = pluginManager.getAllPlugins().map { plugin ->
                mapOf(
                    "name" to plugin.getPluginName(),
                    "description" to plugin.getDescription()
                )
            }

            Response.ok(mapOf("plugins" to plugins)).build()

        } catch (e: Exception) {
            logger.error("Failed to get plugin list: ${e.message}", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Internal server error"))
                .build()
        }
    }

    @GET
    @Path("/channels")
    fun getChannelInfo(): Response {
        return try {
            val rokuPlugin = pluginManager.getPlugin("roku")
            val channelInfo = if (rokuPlugin is com.blockbuster.plugin.roku.RokuPlugin) {
                rokuPlugin.getAllChannelPlugins().map { channel ->
                    mapOf(
                        "channelId" to channel.getChannelId(),
                        "channelName" to channel.getChannelName(),
                        "searchUrl" to channel.getSearchUrl()
                    )
                }
            } else {
                emptyList()
            }

            Response.ok(mapOf("channels" to channelInfo)).build()

        } catch (e: Exception) {
            logger.error("Failed to get channel info: ${e.message}", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Internal server error"))
                .build()
        }
    }
}
