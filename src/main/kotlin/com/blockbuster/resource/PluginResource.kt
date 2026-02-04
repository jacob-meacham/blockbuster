package com.blockbuster.resource

import com.blockbuster.plugin.ChannelInfoProvider
import com.blockbuster.plugin.PluginRegistry
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/{pluginName}")
@Produces(MediaType.APPLICATION_JSON)
class PluginResource(
    private val plugins: PluginRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GET
    @Path("/channels")
    fun getChannelInfo(
        @PathParam("pluginName") pluginName: String,
    ): Response {
        return try {
            val plugin =
                plugins[pluginName]
                    ?: return Response.status(Response.Status.NOT_FOUND)
                        .entity(ErrorResponse("Plugin '$pluginName' not found"))
                        .build()

            val channels =
                if (plugin is ChannelInfoProvider) {
                    plugin.getChannelInfo()
                } else {
                    emptyList()
                }

            Response.ok(ChannelListResponse(channels = channels)).build()
        } catch (e: Exception) {
            logger.error("Failed to get channel info for plugin '{}': {}", pluginName, e.message, e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ErrorResponse("Internal server error"))
                .build()
        }
    }
}
