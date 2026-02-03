package com.blockbuster.resource

import com.blockbuster.db.FlywayManager
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.sqlite.SQLiteDataSource
import javax.sql.DataSource

class HealthResourceTest {
    private lateinit var mockFlywayManager: FlywayManager
    private lateinit var dataSource: DataSource
    private lateinit var healthResource: HealthResource

    @BeforeEach
    fun setUp() {
        mockFlywayManager = mock()
        dataSource =
            SQLiteDataSource().apply {
                url = "jdbc:sqlite:file:healthtest_${System.nanoTime()}?mode=memory&cache=shared"
            }
        healthResource = HealthResource(mockFlywayManager, dataSource)
    }

    @Test
    fun `health returns 200 with status and migration info when db is healthy`() {
        whenever(mockFlywayManager.getMigrationInfo()).thenReturn("Migrations: 3 applied, 0 pending")

        val response = healthResource.health()

        assertEquals(Response.Status.OK.statusCode, response.status)
        @Suppress("UNCHECKED_CAST")
        val entity = response.entity as Map<String, Any>
        assertEquals("healthy", entity["status"])
        assertEquals(true, entity["database"])
        assertEquals("Migrations: 3 applied, 0 pending", entity["migrations"])
    }

    @Test
    fun `health returns 500 when flyway throws exception`() {
        whenever(mockFlywayManager.getMigrationInfo()).thenThrow(RuntimeException("DB connection failed"))

        val response = healthResource.health()

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.statusCode, response.status)
        @Suppress("UNCHECKED_CAST")
        val entity = response.entity as Map<String, Any?>
        assertEquals("error", entity["status"])
        assertEquals("DB connection failed", entity["message"])
    }

    @Test
    fun `live always returns 200`() {
        val response = healthResource.live()

        assertEquals(Response.Status.OK.statusCode, response.status)
        @Suppress("UNCHECKED_CAST")
        val entity = response.entity as Map<String, Any>
        assertEquals("alive", entity["status"])
    }

    @Test
    fun `ready returns 200 when database is accessible`() {
        val response = healthResource.ready()

        assertEquals(Response.Status.OK.statusCode, response.status)
        @Suppress("UNCHECKED_CAST")
        val entity = response.entity as Map<String, Any>
        assertEquals("ready", entity["status"])
    }

    @Test
    fun `ready returns 503 when database is not accessible`() {
        val badDataSource: DataSource = mock()
        whenever(badDataSource.connection).thenThrow(RuntimeException("Connection refused"))
        val resource = HealthResource(mockFlywayManager, badDataSource)

        val response = resource.ready()

        assertEquals(Response.Status.SERVICE_UNAVAILABLE.statusCode, response.status)
        @Suppress("UNCHECKED_CAST")
        val entity = response.entity as Map<String, Any?>
        assertEquals("not ready", entity["status"])
    }
}
