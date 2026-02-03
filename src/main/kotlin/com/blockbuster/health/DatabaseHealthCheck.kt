package com.blockbuster.health

import com.codahale.metrics.health.HealthCheck
import javax.sql.DataSource

/**
 * Dropwizard health check that verifies the SQLite database is reachable.
 *
 * Executes a lightweight `SELECT 1` query to confirm that a connection
 * can be obtained and the database is responsive.
 *
 * @property dataSource the JDBC DataSource to check
 */
class DatabaseHealthCheck(private val dataSource: DataSource) : HealthCheck() {

    override fun check(): Result {
        return try {
            dataSource.connection.use { connection ->
                connection.prepareStatement("SELECT 1").use { statement ->
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            Result.healthy()
                        } else {
                            Result.unhealthy("SELECT 1 returned no rows")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Result.unhealthy(e)
        }
    }
}
