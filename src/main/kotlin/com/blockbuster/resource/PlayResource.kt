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
 * GET serves the React SPA (browser/NFC tap). POST triggers actual playback (appliance/API).
 */
@Path("/play")
@Produces(MediaType.APPLICATION_JSON)
class PlayResource(
    private val mediaStore: MediaStore,
    private val plugins: PluginRegistry,
    private val theaterManager: TheaterDeviceManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * GET endpoint for browser access (e.g., from NFC tap on phone).
     * Serves the React SPA which handles rendering the play page client-side.
     */
    @GET
    @Path("/{uuid}")
    @Produces(MediaType.TEXT_HTML)
    @Suppress("detekt:UnusedParameter")
    fun playPage(
        @PathParam("uuid") uuid: String,
    ): Response {
        val stream =
            javaClass.classLoader.getResourceAsStream("frontend/index.html")
                ?: return Response.status(Response.Status.NOT_FOUND)
                    .entity("<h1>Frontend not built. Run: cd frontend && npm run build</h1>")
                    .type(MediaType.TEXT_HTML)
                    .build()
        val content = stream.bufferedReader().use { it.readText() }
        return Response.ok(content, MediaType.TEXT_HTML).build()
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
