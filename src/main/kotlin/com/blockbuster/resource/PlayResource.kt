package com.blockbuster.resource

import com.blockbuster.media.MediaStore
import com.blockbuster.plugin.PluginRegistry
import com.blockbuster.theater.TheaterDeviceManager
import com.blockbuster.theater.TheaterSetupException
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

/**
 * REST resource for triggering media playback.
 *
 * Supports both GET (browser/NFC tap) and POST (appliance/API) access.
 */
@Path("/play")
@Produces(MediaType.APPLICATION_JSON)
class PlayResource(
    private val mediaStore: MediaStore,
    private val plugins: PluginRegistry,
    private val theaterManager: TheaterDeviceManager,
) {
    companion object {
        fun escapeHtml(s: String): String =
            s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * GET endpoint for browser access (e.g., from NFC tap on phone).
     * Returns an HTML page with a "Play" button.
     */
    @GET
    @Path("/{uuid}")
    @Produces(MediaType.TEXT_HTML)
    fun playPage(
        @PathParam("uuid") uuid: String,
    ): String {
        val safeUuid = escapeHtml(uuid)

        val mediaItem =
            mediaStore.get(uuid)
                ?: return loadTemplate("not-found.html")
                    .replace("{{uuid}}", safeUuid)

        return loadTemplate("play.html")
            .replace("{{uuid}}", safeUuid)
            .replace("{{plugin}}", escapeHtml(mediaItem.plugin))
    }

    private fun loadTemplate(name: String): String {
        return javaClass.classLoader.getResourceAsStream("templates/$name")
            ?.bufferedReader()?.use { it.readText() }
            ?: "<h1>Template not found: $name</h1>"
    }

    /**
     * POST endpoint for appliance/API access.
     * Triggers theater setup (if deviceId provided) and media playback.
     */
    @POST
    @Path("/{uuid}")
    fun play(
        @PathParam("uuid") uuid: String,
        @QueryParam("deviceId") deviceId: String?,
    ): Response {
        logger.info("Play request: uuid={} deviceId={}", uuid, deviceId)

        val mediaItem =
            mediaStore.get(uuid)
                ?: return Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Content not found", "uuid" to uuid))
                    .build()

        val plugin =
            plugins[mediaItem.plugin]
                ?: return Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Plugin not found", "plugin" to mediaItem.plugin))
                    .build()

        return try {
            // Execute theater setup if device specified (best-effort)
            deviceId?.let {
                logger.info("Setting up theater for device: {}", it)
                try {
                    theaterManager.setupTheater(it)
                } catch (e: TheaterSetupException) {
                    logger.warn("Theater setup failed for device {}, continuing with playback: {}", it, e.message)
                }
            }

            // Execute playback
            logger.info("Starting playback via plugin: {}", mediaItem.plugin)
            plugin.play(uuid)

            Response.ok(
                mapOf(
                    "status" to "playing",
                    "uuid" to uuid,
                    "plugin" to mediaItem.plugin,
                    "device" to (deviceId ?: "direct"),
                ),
            ).build()
        } catch (e: IllegalArgumentException) {
            // Unknown deviceId
            logger.error("Invalid device: {}", e.message)
            Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to e.message))
                .build()
        } catch (e: Exception) {
            logger.error("Playback failed: {}", e.message, e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to (e.message ?: "Playback failed")))
                .build()
        }
    }
}
