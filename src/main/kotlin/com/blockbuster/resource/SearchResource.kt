package com.blockbuster.resource

import com.blockbuster.plugin.MediaPluginManager
import com.blockbuster.plugin.PluginException
import com.blockbuster.media.RokuMediaContent
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/search")
@Produces(MediaType.APPLICATION_JSON)
class SearchResource(private val pluginManager: MediaPluginManager) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @GET
    @Path("/{pluginName}")
    fun search(
        @PathParam("pluginName") pluginName: String,
        @QueryParam("q") query: String,
        @QueryParam("limit") limit: Int = 10
    ): Response {
        return try {
            if (query.isNullOrBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(mapOf("error" to "Query parameter 'q' is required"))
                    .build()
            }

            val plugin = pluginManager.getPlugin(pluginName)
                ?: return Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Plugin '$pluginName' not found"))
                    .build()

            logger.info("Searching plugin '$pluginName' for query '$query'")

            val results = plugin.search(query, mapOf("limit" to limit))

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
}
