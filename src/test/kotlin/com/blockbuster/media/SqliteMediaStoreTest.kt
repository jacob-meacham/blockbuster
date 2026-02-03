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

    @Test
    fun `should store and retrieve content with metadata`() {
        val content = RokuMediaContent(
            channelName = "Emby",
            channelId = "44191",
            contentId = "12345",
            title = "Inception",
            mediaType = "Movie",
            metadata = RokuMediaMetadata(
                resumePositionTicks = 1800L,
                officialRating = "PG-13",
                year = 2010
            )
        )

        val uuid = mediaStore.put("roku", content)
        val retrieved = mediaStore.getParsed(uuid, "roku", RokuMediaContent)

        assertNotNull(retrieved)
        assertNotNull(retrieved!!.metadata)
        assertEquals(1800L, retrieved.metadata!!.resumePositionTicks)
        assertEquals("PG-13", retrieved.metadata!!.officialRating)
        assertEquals(2010, retrieved.metadata!!.year)
    }

    @Test
    fun `should list content by plugin`() {
        mediaStore.put("roku", RokuMediaContent(channelId = "12", contentId = "1", title = "Movie 1"))
        mediaStore.put("roku", RokuMediaContent(channelId = "44191", contentId = "2", title = "Movie 2"))

        val items = mediaStore.list(0, 10, "roku")

        assertEquals(2, items.size)
        assertTrue(items.all { it.plugin == "roku" })
    }

    @Test
    fun `should count content by plugin`() {
        mediaStore.put("roku", RokuMediaContent(channelId = "12", contentId = "1", title = "Title 1"))
        mediaStore.put("roku", RokuMediaContent(channelId = "44191", contentId = "2", title = "Title 2"))
        mediaStore.put("roku", RokuMediaContent(channelId = "291097", contentId = "3", title = "Title 3"))

        assertEquals(3, mediaStore.count("roku"))
    }

    @Test
    fun `should handle pagination`() {
        for (i in 1..5) {
            mediaStore.put("roku", RokuMediaContent(channelId = "12", contentId = "id$i", title = "Title $i"))
        }

        val page1 = mediaStore.list(0, 2, "roku")
        assertEquals(2, page1.size)

        val page2 = mediaStore.list(2, 2, "roku")
        assertEquals(2, page2.size)

        val page3 = mediaStore.list(4, 2, "roku")
        assertEquals(1, page3.size)

        val allUuids = (page1 + page2 + page3).map { it.uuid }.toSet()
        assertEquals(5, allUuids.size)
    }
}
