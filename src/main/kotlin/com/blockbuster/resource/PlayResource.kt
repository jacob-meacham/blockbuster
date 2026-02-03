package com.blockbuster.resource

import com.blockbuster.media.MediaStore
import com.blockbuster.plugin.MediaPluginManager
import com.blockbuster.theater.TheaterDeviceManager
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
    private val pluginManager: MediaPluginManager,
    private val theaterManager: TheaterDeviceManager
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * GET endpoint for browser access (e.g., from NFC tap on phone).
     * Returns an HTML page with a "Play" button.
     */
    @GET
    @Path("/{uuid}")
    @Produces(MediaType.TEXT_HTML)
    fun playPage(@PathParam("uuid") uuid: String): String {
        val mediaItem = mediaStore.get(uuid)
            ?: return """
                <html>
                <head><title>Content Not Found</title></head>
                <body style="font-family: sans-serif; margin: 40px;">
                    <h1>Content Not Found</h1>
                    <p>UUID: $uuid</p>
                </body>
                </html>
            """.trimIndent()

        return """
            <html>
            <head>
                <title>Play Content</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
            </head>
            <body style="font-family: sans-serif; margin: 40px;">
                <h1>${mediaItem.plugin} Content</h1>
                <p>UUID: $uuid</p>
                <button onclick="play()" style="padding: 20px 40px; font-size: 18px; cursor: pointer;">
                    Play Now
                </button>
                <div id="status" style="margin-top: 20px;"></div>
                <script>
                    function play() {
                        document.getElementById('status').innerHTML = 'Starting playback...';
                        fetch('/play/$uuid', { method: 'POST' })
                            .then(r => r.json())
                            .then(data => {
                                document.getElementById('status').innerHTML = '<h2>Playing...</h2><pre>' + JSON.stringify(data, null, 2) + '</pre>';
                            })
                            .catch(e => {
                                document.getElementById('status').innerHTML = '<h2 style="color: red;">Error: ' + e.message + '</h2>';
                            });
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * POST endpoint for appliance/API access.
     * Triggers theater setup (if deviceId provided) and media playback.
     */
    @POST
    @Path("/{uuid}")
    fun play(
        @PathParam("uuid") uuid: String,
        @QueryParam("deviceId") deviceId: String?
    ): Response {
        logger.info("Play request: uuid=$uuid deviceId=$deviceId")

        val mediaItem = mediaStore.get(uuid)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(mapOf("error" to "Content not found", "uuid" to uuid))
                .build()

        val plugin = pluginManager.getPlugin(mediaItem.plugin)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(mapOf("error" to "Plugin not found", "plugin" to mediaItem.plugin))
                .build()

        return try {
            // Execute theater setup if device specified
            deviceId?.let {
                logger.info("Setting up theater for device: $it")
                theaterManager.setupTheater(it)
            }

            // Execute playback
            logger.info("Starting playback via plugin: ${mediaItem.plugin}")
            plugin.play(uuid)

            Response.ok(mapOf(
                "status" to "playing",
                "uuid" to uuid,
                "plugin" to mediaItem.plugin,
                "device" to (deviceId ?: "direct")
            )).build()
        } catch (e: IllegalArgumentException) {
            // Unknown deviceId
            logger.error("Invalid device: ${e.message}")
            Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to e.message))
                .build()
        } catch (e: Exception) {
            logger.error("Playback failed: ${e.message}", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to (e.message ?: "Playback failed")))
                .build()
        }
    }
}
