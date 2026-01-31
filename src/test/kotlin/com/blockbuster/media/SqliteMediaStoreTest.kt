package com.blockbuster.media

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import com.blockbuster.db.FlywayManager

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqliteMediaStoreTest {
    
    private lateinit var mediaStore: SqliteMediaStore
    private lateinit var dataSource: org.sqlite.SQLiteDataSource
    private lateinit var connection: java.sql.Connection
    private val testDbPath = "file:testdb?mode=memory&cache=shared"
    
    @BeforeEach
    fun setUp() {
        dataSource = org.sqlite.SQLiteDataSource().apply {
            url = "jdbc:sqlite:$testDbPath"
        }
        connection = dataSource.connection
        val flywayManager = FlywayManager(dataSource, testDbPath)
        flywayManager.migrate()
        mediaStore = SqliteMediaStore(dataSource)
    }
    
    @AfterEach
    fun tearDown() {
        connection.close()
    }
    
    @Test
    fun `should store and retrieve media content`() {
        val content = RokuMediaContent(
            channelId = "netflix",
            contentId = "movie123",
            mediaType = "movie",
            title = "The Matrix"
        )
        val uuid = mediaStore.put("roku", content)
        val retrieved = mediaStore.getParsed(uuid, "roku", RokuMediaContent)
        assertNotNull(retrieved)
        retrieved?.let { c ->
            assertEquals(content.channelId, c.channelId)
            assertEquals(content.contentId, c.contentId)
            assertEquals(content.mediaType, c.mediaType)
            assertEquals(content.title, c.title)
        }
        val item = mediaStore.get(uuid)
        assertNotNull(item)
        assertEquals("roku", item!!.plugin)
    }
    
    @Test
    fun `should update existing media content`() {
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
        val uuid = mediaStore.put("roku", originalContent)
        val item1 = mediaStore.get(uuid)!!
        mediaStore.update(uuid, "roku", updatedContent)
        val item2 = mediaStore.get(uuid)!!
        assertTrue(item2.updatedAt.isAfter(item1.updatedAt))
        assertEquals(item1.createdAt, item2.createdAt)
        val parsed = mediaStore.getParsed(uuid, "roku", RokuMediaContent)!!
        assertEquals(updatedContent.title, parsed.title)
        assertEquals(updatedContent.contentId, parsed.contentId)
    }
    
    @Test
    fun `should return null for non-existent UUID`() {
        val result = mediaStore.getParsed("non-existent-uuid", "roku", RokuMediaContent)
        assertNull(result)
    }
    
    @Test
    fun `should remove media content`() {
        val content = RokuMediaContent(
            channelId = "hbo",
            contentId = "show123",
            mediaType = "show",
            title = "Game of Thrones"
        )
        val uuid = mediaStore.put("roku", content)
        assertNotNull(mediaStore.get(uuid))
        mediaStore.remove(uuid)
        val result = mediaStore.get(uuid)
        assertNull(result)
    }
    
    @Test
    fun `should handle removal of non-existent content gracefully`() {
        assertDoesNotThrow {
            mediaStore.remove("non-existent-uuid")
        }
    }
    
    @Test
    fun `should store content with minimal required fields`() {
        val content = RokuMediaContent(
            channelId = "basic-channel",
            contentId = "basic-content"
        )
        val uuid = mediaStore.put("roku", content)
        val retrieved = mediaStore.getParsed(uuid, "roku", RokuMediaContent)
        assertNotNull(retrieved)
        retrieved?.let { c ->
            assertEquals("basic-channel", c.channelId)
            assertEquals("basic-content", c.contentId)
            assertNull(c.mediaType)
            assertNull(c.title)
        }
    }
    
    @Test
    fun `should handle special characters in content`() {
        val content = RokuMediaContent(
            channelId = "netflix",
            contentId = "movie-123",
            mediaType = "movie",
            title = "The Matrix: Reloaded (2003) - Sci-Fi/Action"
        )
        val uuid = mediaStore.put("roku", content)
        val retrieved = mediaStore.getParsed(uuid, "roku", RokuMediaContent)
        assertNotNull(retrieved)
        assertEquals(content.title, retrieved?.title)
    }
    
    @Test
    fun `should handle multiple content entries`() {
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
        val uuids = contents.map { mediaStore.put("roku", it) }
        uuids.forEachIndexed { index, uuid ->
            val retrieved = mediaStore.getParsed(uuid, "roku", RokuMediaContent)
            assertNotNull(retrieved)
            assertEquals(contents[index].title, retrieved?.title)
        }
    }
}
