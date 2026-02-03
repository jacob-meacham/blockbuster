package com.blockbuster.resource

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

/**
 * Serves the frontend SPA and static assets from the classpath.
 *
 * In production, the Vite build output is embedded in the JAR under "frontend/".
 * In dev mode, the Vite dev server handles everything and this resource is unused.
 */
@Path("/")
class FrontendResource {

    private val mimeTypes = mapOf(
        "js" to "application/javascript",
        "css" to "text/css",
        "html" to "text/html",
        "json" to "application/json",
        "svg" to "image/svg+xml",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "ico" to "image/x-icon",
        "woff" to "font/woff",
        "woff2" to "font/woff2",
        "ttf" to "font/ttf",
        "eot" to "application/vnd.ms-fontobject"
    )

    @GET
    @Produces(MediaType.TEXT_HTML)
    fun index(): Response = serveIndexHtml()

    @GET
    @Path("assets/{path:.*}")
    fun staticAsset(@PathParam("path") path: String): Response {
        val resourcePath = "frontend/assets/$path"
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: return Response.status(Response.Status.NOT_FOUND).build()

        val extension = path.substringAfterLast('.', "")
        val contentType = mimeTypes[extension] ?: "application/octet-stream"

        return Response.ok(stream, contentType).build()
    }

    private fun serveIndexHtml(): Response {
        val stream = javaClass.classLoader.getResourceAsStream("frontend/index.html")
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity("<h1>Frontend not built. Run: cd frontend && npm run build</h1>")
                .type(MediaType.TEXT_HTML)
                .build()
        val content = stream.bufferedReader().use { it.readText() }
        return Response.ok(content, MediaType.TEXT_HTML).build()
    }
}
