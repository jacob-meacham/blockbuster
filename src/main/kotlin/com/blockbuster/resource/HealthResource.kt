package com.blockbuster.resource

import com.blockbuster.db.FlywayManager
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
class HealthResource(private val flywayManager: FlywayManager) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @GET
    fun health(): Response {
        return try {
            val migrationInfo = flywayManager.getMigrationInfo()
            val isHealthy = true // For now, just assume healthy if we get here

            val response = mapOf(
                "status" to "healthy",
                "migrations" to migrationInfo,
                "overall" to isHealthy
            )

            Response.ok(response).build()
        } catch (e: Exception) {
            logger.error("Health check failed", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf(
                    "status" to "error",
                    "message" to e.message,
                    "overall" to false
                ))
                .build()
        }
    }
}
