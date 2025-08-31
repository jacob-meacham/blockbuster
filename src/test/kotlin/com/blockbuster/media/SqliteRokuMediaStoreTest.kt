package com.blockbuster.media

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.io.File
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import com.blockbuster.db.FlywayManager

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqliteRokuMediaStoreTest {
    
    private lateinit var mediaStore: SqliteRokuMediaStore
    private lateinit var dataSource: org.sqlite.SQLiteDataSource
    private lateinit var connection: java.sql.Connection
    private val testDbPath = "file:testdb?mode=memory&cache=shared"
    
    @BeforeEach
    fun setUp() {
        // Create a shared SQLiteDataSource and connection for the test
        dataSource = org.sqlite.SQLiteDataSource().apply {
            url = "jdbc:sqlite:$testDbPath"
        }
        
        // Get a connection and keep it open
        connection = dataSource.connection
        
        // Run Flyway migrations first, independently
        val flywayManager = FlywayManager(dataSource, testDbPath)
        flywayManager.migrate()

        mediaStore = SqliteRokuMediaStore(dataSource)
    }
    
    @AfterEach
    fun tearDown() {
        mediaStore.close()
        connection.close()
    }
    
    @Test
    fun `should store and retrieve media content`() {
        // Given
        val uuid = "test-uuid-123"
        val content = RokuMediaContent(
            channelId = "netflix",
            contentId = "movie123",
            mediaType = "movie",
            title = "The Matrix"
        )
        
        // When
        mediaStore.storeMediaContent(uuid, content)
        val retrieved = mediaStore.getMediaContent(uuid)
        
        // Then
        assertNotNull(retrieved)
        retrieved?.let { retrievedContent ->
            assertEquals(content.channelId, retrievedContent.channelId)
            assertEquals(content.contentId, retrievedContent.contentId)
            assertEquals(content.mediaType, retrievedContent.mediaType)
            assertEquals(content.title, retrievedContent.title)
            assertNotNull(retrievedContent.createdAt)
            assertNotNull(retrievedContent.updatedAt)
        }
    }
    
    @Test
    fun `should update existing media content`() {
        // Given
        val uuid = "test-uuid-456"
        val originalContent = RokuMediaContent(
            channelId = "netflix",
            contentId = "movie123",
            mediaType = "movie",
            title = "The Matrix"
        )
        
        val updatedContent = RokuMediaContent(
            channelId = "netflix",
            contentId = "movie456",
            mediaType = "movie",
            title = "The Matrix Reloaded"
        )
        
        // When
        mediaStore.storeMediaContent(uuid, originalContent)
        val originalCreatedAt = mediaStore.getMediaContent(uuid)?.createdAt
        
        mediaStore.storeMediaContent(uuid, updatedContent)
        val retrieved = mediaStore.getMediaContent(uuid)
        
        // Then
        assertNotNull(retrieved)
        retrieved?.let { retrievedContent ->
            assertEquals(updatedContent.title, retrievedContent.title)
            assertEquals(updatedContent.contentId, retrievedContent.contentId)
            assertEquals(originalCreatedAt, retrievedContent.createdAt) // created_at should not change
            assertTrue(retrievedContent.updatedAt.isAfter(originalCreatedAt!!)) // updated_at should change
        }
    }
    
    @Test
    fun `should return null for non-existent UUID`() {
        // When
        val result = mediaStore.getMediaContent("non-existent-uuid")
        
        // Then
        assertNull(result)
    }
    
    @Test
    fun `should remove media content`() {
        // Given
        val uuid = "test-uuid-789"
        val content = RokuMediaContent(
            channelId = "hbo",
            contentId = "show123",
            mediaType = "show",
            title = "Game of Thrones"
        )
        
        // When
        mediaStore.storeMediaContent(uuid, content)
        assertNotNull(mediaStore.getMediaContent(uuid))
        
        mediaStore.removeMediaContent(uuid)
        val result = mediaStore.getMediaContent(uuid)
        
        // Then
        assertNull(result)
    }
    
    @Test
    fun `should handle removal of non-existent content gracefully`() {
        // When/Then - should not throw exception
        assertDoesNotThrow {
            mediaStore.removeMediaContent("non-existent-uuid")
        }
    }
    
    @Test
    fun `should store content with minimal required fields`() {
        // Given
        val uuid = "minimal-uuid"
        val content = RokuMediaContent(
            channelId = "basic-channel",
            contentId = "basic-content"
        )
        
        // When
        mediaStore.storeMediaContent(uuid, content)
        val retrieved = mediaStore.getMediaContent(uuid)
        
        // Then
        assertNotNull(retrieved)
        retrieved?.let { retrievedContent ->
            assertEquals("basic-channel", retrievedContent.channelId)
            assertEquals("basic-content", retrievedContent.contentId)
            assertEquals("launch", retrievedContent.ecpCommand) // default value
            assertNull(retrievedContent.mediaType)
            assertNull(retrievedContent.title)
        }
    }
    
    @Test
    fun `should handle special characters in content`() {
        // Given
        val uuid = "special-chars-uuid"
        val content = RokuMediaContent(
            channelId = "netflix",
            contentId = "movie-123",
            mediaType = "movie",
            title = "The Matrix: Reloaded (2003) - Sci-Fi/Action"
        )
        
        // When
        mediaStore.storeMediaContent(uuid, content)
        val retrieved = mediaStore.getMediaContent(uuid)
        
        // Then
        assertNotNull(retrieved)
        retrieved?.let { retrievedContent ->
            assertEquals(content.title, retrievedContent.title)
        }
    }
    
    @Test
    fun `should handle multiple content entries`() {
        // Given
        val contents = listOf(
            RokuMediaContent(
                channelId = "netflix",
                contentId = "movie1",
                mediaType = "movie",
                title = "Movie 1"
            ),
            RokuMediaContent(
                channelId = "hbo",
                contentId = "show1",
                mediaType = "show",
                title = "Show 1"
            ),
            RokuMediaContent(
                channelId = "netflix",
                contentId = "movie2",
                mediaType = "movie",
                title = "Movie 2"
            )
        )
        
        // When
        contents.forEachIndexed { index, content ->
            mediaStore.storeMediaContent("uuid-$index", content)
        }
        
        // Then
        contents.forEachIndexed { index, content ->
            val retrieved = mediaStore.getMediaContent("uuid-$index")
            assertNotNull(retrieved)
            retrieved?.let { retrievedContent ->
                assertEquals(content.title, retrievedContent.title)
            }
        }
    }
}
