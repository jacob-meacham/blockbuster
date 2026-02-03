package com.blockbuster.resource

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/")
class StaticResource {

    private val logger = LoggerFactory.getLogger(javaClass)

    private fun serveIndexHtml(): Response {
        return try {
            val inputStream = javaClass.classLoader.getResourceAsStream("assets/index.html")
                ?: return Response.status(Response.Status.NOT_FOUND)
                    .entity("<h1>404 - Page not found</h1>")
                    .build()

            val content = inputStream.bufferedReader().use { it.readText() }
            Response.ok(content).build()

        } catch (e: Exception) {
            logger.error("Failed to serve index page: ${e.message}", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("<h1>500 - Internal Server Error</h1>")
                .build()
        }
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    fun getIndex(): Response = serveIndexHtml()

    @GET
    @Path("/static/{path:.*}")
    fun getStaticFile(@PathParam("path") path: String): Response {
        return try {
            val inputStream = javaClass.classLoader.getResourceAsStream("assets/$path")
                ?: return Response.status(Response.Status.NOT_FOUND).build()

            val mediaType = when {
                path.endsWith(".css") -> "text/css"
                path.endsWith(".js") -> "application/javascript"
                path.endsWith(".png") -> "image/png"
                path.endsWith(".jpg") -> "image/jpeg"
                path.endsWith(".jpeg") -> "image/jpeg"
                path.endsWith(".gif") -> "image/gif"
                path.endsWith(".svg") -> "image/svg+xml"
                path.endsWith(".woff2") -> "font/woff2"
                path.endsWith(".woff") -> "font/woff"
                else -> "application/octet-stream"
            }

            Response.ok(inputStream.readBytes(), mediaType).build()

        } catch (e: Exception) {
            logger.error("Failed to serve static file $path: ${e.message}", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).build()
        }
    }
}
