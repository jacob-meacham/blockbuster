package com.blockbuster.resource

import com.blockbuster.plugin.AuthenticablePlugin
import com.blockbuster.plugin.PluginRegistry
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * Generic OAuth endpoints for any plugin that implements [AuthenticablePlugin].
 *
 * Routes are `GET /auth/{plugin}`, `GET /auth/{plugin}/callback`, and
 * `GET /auth/{plugin}/status`. No plugin-specific code lives here; the plugin
 * itself handles building the authorization URL and exchanging the code.
 */
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
class AuthResource(
    private val plugins: PluginRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Initiate the OAuth flow by redirecting the user to the plugin's authorization URL.
     */
    @GET
    @Path("/{plugin}")
    fun initiateAuth(
        @PathParam("plugin") pluginName: String,
        @Context uriInfo: UriInfo,
    ): Response {
        val authenticable =
            findAuthenticable(pluginName)
                ?: return notFoundResponse(pluginName)

        val callbackBaseUrl = resolveBaseUrl(uriInfo)
        val authUrl = authenticable.buildAuthorizationUrl(callbackBaseUrl)

        logger.info("Redirecting to OAuth for plugin '{}'", pluginName)
        return Response.seeOther(URI.create(authUrl)).build()
    }

    /**
     * Handle the OAuth callback from the provider.
     */
    @GET
    @Path("/{plugin}/callback")
    @Produces(MediaType.TEXT_HTML)
    fun authCallback(
        @PathParam("plugin") pluginName: String,
        @QueryParam("code") code: String?,
        @QueryParam("error") error: String?,
        @Context uriInfo: UriInfo,
    ): Response {
        val authenticable =
            findAuthenticable(pluginName)
                ?: return notFoundResponse(pluginName)

        if (error != null) {
            logger.warn("OAuth error for plugin '{}': {}", pluginName, error)
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse("OAuth error: $error"))
                .build()
        }

        if (code == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse("Missing 'code' parameter"))
                .build()
        }

        return try {
            val callbackBaseUrl = resolveBaseUrl(uriInfo)
            authenticable.handleAuthCallback(code, callbackBaseUrl)
            logger.info("OAuth completed for plugin '{}'", pluginName)
            Response.ok(
                "<html><body><h2>Authentication successful!</h2>" +
                    "<p>You can close this window.</p></body></html>",
                MediaType.TEXT_HTML,
            ).build()
        } catch (e: Exception) {
            logger.error("OAuth callback failed for plugin '{}': {}", pluginName, e.message, e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ErrorResponse("Authentication failed: ${e.message}"))
                .build()
        }
    }

    /**
     * Check authentication status for a plugin.
     */
    @GET
    @Path("/{plugin}/status")
    fun authStatus(
        @PathParam("plugin") pluginName: String,
    ): Response {
        val plugin =
            plugins[pluginName]
                ?: return notFoundResponse(pluginName)

        val authenticable = plugin as? AuthenticablePlugin

        return Response.ok(
            AuthStatusResponse(
                plugin = pluginName,
                available = authenticable != null,
                authenticated = authenticable?.isAuthenticated() ?: false,
            ),
        ).build()
    }

    private fun findAuthenticable(pluginName: String): AuthenticablePlugin? {
        val plugin = plugins[pluginName] ?: return null
        return plugin as? AuthenticablePlugin
    }

    private fun notFoundResponse(pluginName: String): Response {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(ErrorResponse("Plugin '$pluginName' not found or does not support authentication"))
            .build()
    }

    /**
     * Derive the base URL from the incoming request (scheme + host + port).
     */
    private fun resolveBaseUrl(uriInfo: UriInfo): String {
        val base = uriInfo.baseUri
        val port = if (base.port == -1 || base.port == 80 || base.port == 443) "" else ":${base.port}"
        return "${base.scheme}://${base.host}$port"
    }
}

data class AuthStatusResponse(
    val plugin: String,
    val available: Boolean,
    val authenticated: Boolean,
)
