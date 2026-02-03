package com.blockbuster.health

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

import javax.sql.DataSource

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DatabaseHealthCheckTest {

    private lateinit var dataSource: DataSource
    private lateinit var connection: Connection
    private lateinit var statement: PreparedStatement
    private lateinit var resultSet: ResultSet

    @BeforeEach
    fun setUp() {
        dataSource = mock()
        connection = mock()
        statement = mock()
        resultSet = mock()
    }

    @Test
    fun `check returns healthy when database responds to SELECT 1`() {
        // Given
        whenever(dataSource.connection).thenReturn(connection)
        whenever(connection.prepareStatement("SELECT 1")).thenReturn(statement)
        whenever(statement.executeQuery()).thenReturn(resultSet)
        whenever(resultSet.next()).thenReturn(true)

        val healthCheck = DatabaseHealthCheck(dataSource)

        // When
        val result = healthCheck.execute()

        // Then
        assertTrue(result.isHealthy)
    }

    @Test
    fun `check returns unhealthy when SELECT 1 returns no rows`() {
        // Given
        whenever(dataSource.connection).thenReturn(connection)
        whenever(connection.prepareStatement("SELECT 1")).thenReturn(statement)
        whenever(statement.executeQuery()).thenReturn(resultSet)
        whenever(resultSet.next()).thenReturn(false)

        val healthCheck = DatabaseHealthCheck(dataSource)

        // When
        val result = healthCheck.execute()

        // Then
        assertFalse(result.isHealthy)
        assertTrue(result.message.contains("no rows"))
    }

    @Test
    fun `check returns unhealthy when connection throws SQLException`() {
        // Given
        whenever(dataSource.connection).thenThrow(SQLException("Database file not found"))

        val healthCheck = DatabaseHealthCheck(dataSource)

        // When
        val result = healthCheck.execute()

        // Then
        assertFalse(result.isHealthy)
    }

    @Test
    fun `check returns unhealthy when query execution fails`() {
        // Given
        whenever(dataSource.connection).thenReturn(connection)
        whenever(connection.prepareStatement("SELECT 1")).thenReturn(statement)
        whenever(statement.executeQuery()).thenThrow(SQLException("Disk I/O error"))

        val healthCheck = DatabaseHealthCheck(dataSource)

        // When
        val result = healthCheck.execute()

        // Then
        assertFalse(result.isHealthy)
    }
}
