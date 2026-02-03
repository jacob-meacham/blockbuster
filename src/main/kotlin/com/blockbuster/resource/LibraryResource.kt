package com.blockbuster.resource

import com.blockbuster.media.MediaItem
import com.blockbuster.media.MediaStore
import com.blockbuster.media.MediaJson
import com.blockbuster.plugin.MediaPluginManager
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/library")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class LibraryResource(
    private val pluginManager: MediaPluginManager,
    private val mediaStore: MediaStore,
    private val baseUrl: String = "http://localhost:8080"
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    data class AddByPluginRequest(
        val content: Map<String, Any>,
        val uuid: String? = null
    )

    @GET
    fun list(
        @QueryParam("page") page: Int?,
        @QueryParam("pageSize") pageSize: Int?,
        @QueryParam("plugin") plugin: String?
    ): Response {
        val p = (page ?: 1).coerceAtLeast(1)
        val ps = (pageSize ?: 25).coerceIn(1, 200)
        val offset = (p - 1) * ps

        val items = mediaStore.list(offset, ps, plugin)
        val total = mediaStore.count(plugin)

        return Response.ok(mapOf(
            "items" to items.map { it.toResponseMap(baseUrl) },
            "page" to p,
            "pageSize" to ps,
            "total" to total
        )).build()
    }

    @POST
    @Path("/{pluginName}")
    fun addByPlugin(
        @PathParam("pluginName") pluginName: String,
        request: AddByPluginRequest
    ): Response {
        return try {
            val plugin = pluginManager.getPlugin(pluginName.lowercase())
            ?: throw BadRequestException("Unsupported plugin: $pluginName")

            val parser = plugin.getContentParser()
            val json = MediaJson.mapper.writeValueAsString(request.content)
            val typed = parser.fromJson(json)
            val assignedUuid = request.uuid?.also {
                mediaStore.update(it, pluginName.lowercase(), typed)
            } ?: mediaStore.put(pluginName.lowercase(), typed)

            val blockbusterUrl = "${baseUrl.trimEnd('/')}/play/$assignedUuid"

            logger.info("Stored content for plugin=$pluginName uuid=$assignedUuid")
            Response.ok(mapOf(
                "uuid" to assignedUuid,
                "url" to blockbusterUrl
            )).build()
        } catch (e: Exception) {
            logger.error("Failed to add to library: ${e.message}", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Failed to add to library"))
                .build()
        }
    }
}
private fun MediaItem.toResponseMap(baseUrl: String): Map<String, Any?> = mapOf(
    "plugin" to plugin,
    "uuid" to uuid,
    "configJson" to configJson,
    "updatedAt" to updatedAt.toString(),
    "playUrl" to "${baseUrl.trimEnd('/')}/play/$uuid"
)


