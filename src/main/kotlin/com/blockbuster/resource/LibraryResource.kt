package com.blockbuster.resource

import com.blockbuster.media.MediaItem
import com.blockbuster.media.MediaJson
import com.blockbuster.media.MediaStore
import com.blockbuster.plugin.PluginRegistry
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
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
    private val plugins: PluginRegistry,
    private val mediaStore: MediaStore,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    data class AddByPluginRequest(
        val content: Map<String, Any>,
        val uuid: String? = null,
    )

    data class RenameRequest(
        val title: String,
    )

    @GET
    fun list(
        @QueryParam("page") page: Int?,
        @QueryParam("pageSize") pageSize: Int?,
        @QueryParam("plugin") plugin: String?,
    ): Response {
        val p = (page ?: 1).coerceAtLeast(1)
        val ps = (pageSize ?: 25).coerceIn(1, 200)
        val offset = (p - 1) * ps

        val items = mediaStore.list(offset, ps, plugin)
        val total = mediaStore.count(plugin)

        return Response.ok(
            mapOf(
                "items" to items.map { it.toResponseMap() },
                "page" to p,
                "pageSize" to ps,
                "total" to total,
            ),
        ).build()
    }

    @GET
    @Path("/{uuid}")
    fun getItem(
        @PathParam("uuid") uuid: String,
    ): Response {
        val item =
            mediaStore.get(uuid)
                ?: return Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Item not found"))
                    .build()

        return Response.ok(item.toResponseMap()).build()
    }

    @POST
    @Path("/{pluginName}")
    fun addByPlugin(
        @PathParam("pluginName") pluginName: String,
        request: AddByPluginRequest,
    ): Response {
        return try {
            val plugin =
                plugins[pluginName.lowercase()]
                    ?: throw BadRequestException("Unsupported plugin: $pluginName")

            val parser = plugin.getContentParser()
            val json = MediaJson.mapper.writeValueAsString(request.content)
            val typed = parser.fromJson(json)
            val assignedUuid = mediaStore.putOrUpdate(request.uuid, pluginName.lowercase(), typed)

            val blockbusterUrl = "/play/$assignedUuid"

            logger.info("Stored content for plugin={} uuid={}", pluginName, assignedUuid)
            Response.ok(
                mapOf(
                    "uuid" to assignedUuid,
                    "url" to blockbusterUrl,
                ),
            ).build()
        } catch (e: BadRequestException) {
            throw e
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid request for plugin={}: {}", pluginName, e.message)
            Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to (e.message ?: "Invalid request")))
                .build()
        } catch (e: Exception) {
            logger.error("Failed to add to library: {}", e.message, e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Failed to add to library"))
                .build()
        }
    }

    @DELETE
    @Path("/{uuid}")
    fun delete(
        @PathParam("uuid") uuid: String,
    ): Response {
        return try {
            if (!mediaStore.remove(uuid)) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Item not found"))
                    .build()
            }
            logger.info("Deleted library item uuid={}", uuid)
            Response.ok(mapOf("deleted" to true)).build()
        } catch (e: Exception) {
            logger.error("Failed to delete library item: {}", e.message, e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Failed to delete item"))
                .build()
        }
    }

    @PATCH
    @Path("/{uuid}/rename")
    fun rename(
        @PathParam("uuid") uuid: String,
        request: RenameRequest,
    ): Response {
        return try {
            if (!mediaStore.rename(uuid, request.title)) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Item not found"))
                    .build()
            }
            logger.info("Renamed library item uuid={} to '{}'", uuid, request.title)
            Response.ok(mapOf("uuid" to uuid, "title" to request.title)).build()
        } catch (e: IllegalArgumentException) {
            Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to (e.message ?: "Invalid title")))
                .build()
        } catch (e: Exception) {
            logger.error("Failed to rename library item: {}", e.message, e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Failed to rename item"))
                .build()
        }
    }
}

private fun MediaItem.toResponseMap(): Map<String, Any?> =
    mapOf(
        "plugin" to plugin,
        "uuid" to uuid,
        "title" to title,
        "configJson" to configJson,
        "updatedAt" to updatedAt.toString(),
        "playUrl" to "/play/$uuid",
    )
