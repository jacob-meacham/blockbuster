package com.blockbuster.db

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sqlite.SQLiteDataSource

class FlywayManagerTest {
    private lateinit var dataSource: SQLiteDataSource

    @BeforeEach
    fun setUp() {
        dataSource =
            SQLiteDataSource().apply {
                url = "jdbc:sqlite:file:flywaytest_${System.nanoTime()}?mode=memory&cache=shared"
            }
    }

    @Test
    fun `migrate runs successfully on fresh database`() {
        val flywayManager = FlywayManager(dataSource, ":memory:")

        // Should not throw - runs all migrations from classpath:db/migration
        flywayManager.migrate()
    }

    @Test
    fun `migrate is idempotent`() {
        val flywayManager = FlywayManager(dataSource, ":memory:")

        flywayManager.migrate()
        // Running again should succeed without errors
        flywayManager.migrate()
    }

    @Test
    fun `getMigrationInfo returns applied and pending counts`() {
        val flywayManager = FlywayManager(dataSource, ":memory:")
        flywayManager.migrate()

        val info = flywayManager.getMigrationInfo()

        assertTrue(info.contains("applied"))
        assertTrue(info.contains("pending"))
    }

    @Test
    fun `getMigrationInfo reports pending before migration`() {
        val flywayManager = FlywayManager(dataSource, ":memory:")

        val info = flywayManager.getMigrationInfo()

        // Before migration, should show migration info (baseline counts as applied)
        assertTrue(info.startsWith("Migrations:"))
    }

    @Test
    fun `migrate throws IllegalStateException on invalid datasource`() {
        val badDataSource =
            SQLiteDataSource().apply {
                url = "jdbc:sqlite:/nonexistent/path/that/cannot/exist/db.sqlite"
            }
        val flywayManager = FlywayManager(badDataSource, "/nonexistent/path")

        assertThrows(IllegalStateException::class.java) {
            flywayManager.migrate()
        }
    }
}
