package com.blockbuster.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Manages database migrations using Flyway
 */
class FlywayManager(
    private val dataSource: DataSource,
    private val databasePath: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Run all pending migrations
     */
    fun migrate() {
        try {
            val flyway = createFlyway()
            val migrations = flyway.migrate()
            logger.info("Applied {} migrations to database: {}", migrations.migrationsExecuted, databasePath)
        } catch (e: Exception) {
            logger.error("Failed to run migrations: {}", e.message, e)
            throw IllegalStateException("Migration failed", e)
        }
    }

    /**
     * Create and configure Flyway instance
     */
    private fun createFlyway(): Flyway {
        val config =
            FluentConfiguration()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .validateOnMigrate(true)

        return Flyway(config)
    }

    /**
     * Get migration info
     */
    fun getMigrationInfo(): String {
        return try {
            val flyway = createFlyway()
            val info = flyway.info()
            val applied = info.applied().size
            val pending = info.pending().size

            "Migrations: $applied applied, $pending pending"
        } catch (e: Exception) {
            "Could not get migration info: " + e.message
        }
    }
}
