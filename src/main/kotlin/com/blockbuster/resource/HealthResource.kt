package com.blockbuster.resource

import com.blockbuster.db.FlywayManager
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import javax.sql.DataSource

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
class HealthResource(
    private val flywayManager: FlywayManager,
    private val dataSource: DataSource,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Full health check including database connectivity and migration status.
     */
    @GET
    fun health(): Response {
        return try {
            val dbHealthy = checkDatabaseConnectivity()
            val migrationInfo = flywayManager.getMigrationInfo()

            val status = if (dbHealthy) "healthy" else "degraded"

            val response =
                mapOf(
                    "status" to status,
                    "database" to dbHealthy,
                    "migrations" to migrationInfo,
                )

            if (dbHealthy) {
                Response.ok(response).build()
            } else {
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(response)
                    .build()
            }
        } catch (e: Exception) {
            logger.error("Health check failed", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(
                    mapOf(
                        "status" to "error",
                        "message" to e.message,
                    ),
                )
                .build()
        }
    }

    /**
     * Liveness probe - returns 200 if the service is running.
     * Does not check dependencies; only confirms the process is alive.
     */
    @GET
    @Path("/live")
    fun live(): Response = Response.ok(mapOf("status" to "alive")).build()

    /**
     * Readiness probe - returns 200 if the service can handle requests.
     * Checks database connectivity to determine readiness.
     */
    @GET
    @Path("/ready")
    fun ready(): Response {
        return try {
            val dbHealthy = checkDatabaseConnectivity()

            if (dbHealthy) {
                Response.ok(mapOf("status" to "ready")).build()
            } else {
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(mapOf("status" to "not ready", "reason" to "database unavailable"))
                    .build()
            }
        } catch (e: Exception) {
            logger.error("Readiness check failed", e)
            Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(mapOf("status" to "not ready", "reason" to e.message))
                .build()
        }
    }

    private fun checkDatabaseConnectivity(): Boolean {
        return try {
            dataSource.connection.use { connection ->
                connection.prepareStatement("SELECT 1").use { ps ->
                    ps.executeQuery().next()
                }
            }
        } catch (e: Exception) {
            logger.warn("Database connectivity check failed: {}", e.message)
            false
        }
    }
}
