package com.blockbuster.resource

import com.blockbuster.plugin.ChannelInfoProvider
import com.blockbuster.plugin.PluginException
import com.blockbuster.plugin.PluginRegistry
import com.blockbuster.plugin.SearchOptions
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
    private val plugins: PluginRegistry,
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
        val inputStream =
            javaClass.classLoader.getResourceAsStream("assets/index.html")
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
        @QueryParam("plugin") plugin: String? = null,
    ): Response {
        return try {
            if (query.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse("Query parameter 'q' is required"))
                    .build()
            }

            logger.info("Aggregated search for query '{}'", query)

            val pluginsToSearch =
                if (plugin != null) {
                    listOfNotNull(plugins[plugin])
                } else {
                    plugins.values.toList()
                }

            val allResults = mutableListOf<SearchResultItem>()

            pluginsToSearch.forEach { searchPlugin ->
                try {
                    val pluginResults = searchPlugin.search(query, SearchOptions(limit = limit))
                    pluginResults.forEach { result ->
                        allResults.add(
                            SearchResultItem(
                                source = result.source,
                                plugin = searchPlugin.getPluginName(),
                                title = result.title,
                                url = result.url,
                                description = result.description,
                                imageUrl = result.imageUrl,
                                dedupKey = result.dedupKey,
                                content = result.content,
                            ),
                        )
                    }
                    logger.info("Plugin '{}' returned {} results", searchPlugin.getPluginName(), pluginResults.size)
                } catch (e: Exception) {
                    logger.warn("Plugin '{}' search failed: {}", searchPlugin.getPluginName(), e.message)
                }
            }

            val dedupResults =
                allResults
                    .distinctBy { it.dedupKey ?: it }
                    .take(limit)

            Response.ok(
                SearchAllResponse(
                    query = query,
                    totalResults = dedupResults.size,
                    results = dedupResults,
                ),
            ).build()
        } catch (e: Exception) {
            logger.error("Aggregated search failed: {}", e.message, e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ErrorResponse("Internal server error"))
                .build()
        }
    }

    @GET
    @Path("/{pluginName}")
    fun search(
        @PathParam("pluginName") pluginName: String,
        @QueryParam("q") query: String,
        @QueryParam("limit") @DefaultValue("10") limit: Int,
    ): Response {
        return try {
            if (query.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse("Query parameter 'q' is required"))
                    .build()
            }

            val plugin =
                plugins[pluginName]
                    ?: return Response.status(Response.Status.NOT_FOUND)
                        .entity(ErrorResponse("Plugin '$pluginName' not found"))
                        .build()

            logger.info("Searching plugin '{}' for query '{}'", pluginName, query)

            val results = plugin.search(query, SearchOptions(limit = limit))

            Response.ok(
                SearchPluginResponse(
                    plugin = pluginName,
                    query = query,
                    totalResults = results.size,
                    results = results,
                ),
            ).build()
        } catch (e: PluginException) {
            logger.error("Plugin search failed: {}", e.message, e)
            Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse(e.message))
                .build()
        } catch (e: Exception) {
            logger.error("Search request failed: {}", e.message, e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ErrorResponse("Internal server error"))
                .build()
        }
    }

    @GET
    @Path("/plugins")
    fun getAvailablePlugins(): Response {
        return try {
            val pluginList =
                plugins.values.map { plugin ->
                    PluginInfo(
                        name = plugin.getPluginName(),
                        description = plugin.getDescription(),
                    )
                }

            Response.ok(PluginListResponse(plugins = pluginList)).build()
        } catch (e: Exception) {
            logger.error("Failed to get plugin list: {}", e.message, e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ErrorResponse("Internal server error"))
                .build()
        }
    }

    @GET
    @Path("/channels")
    fun getChannelInfo(): Response {
        return try {
            val channels =
                plugins.values
                    .filterIsInstance<ChannelInfoProvider>()
                    .flatMap { it.getChannelInfo() }

            Response.ok(ChannelListResponse(channels = channels)).build()
        } catch (e: Exception) {
            logger.error("Failed to get channel info: {}", e.message, e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ErrorResponse("Internal server error"))
                .build()
        }
    }
}
