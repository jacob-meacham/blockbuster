package com.blockbuster.media

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.sqlite.SQLiteDataSource

/**
 * Test that RokuMediaContent works correctly with the database for all channel types.
 */
class RokuMediaContentDatabaseTest {

    private lateinit var dataSource: SQLiteDataSource
    private lateinit var mediaStore: SqliteMediaStore
    private val testDbFile = "build/test-roku-db-${System.currentTimeMillis()}.db"

    @BeforeEach
    fun setup() {
        // Use temporary file database for tests
        dataSource = SQLiteDataSource().apply {
            url = "jdbc:sqlite:$testDbFile"
            setEnforceForeignKeys(true)
        }

        // Create table manually for tests
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS media_library (
                        uuid TEXT PRIMARY KEY,
                        plugin TEXT NOT NULL,
                        config_json TEXT NOT NULL,
                        created_at TIMESTAMP DEFAULT (datetime('now')),
                        updated_at TIMESTAMP DEFAULT (datetime('now'))
                    )
                """.trimIndent())

                statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_library_plugin ON media_library(plugin)
                """.trimIndent())
            }
        }

        mediaStore = SqliteMediaStore(dataSource)
    }

    @AfterEach
    fun tearDown() {
        // Close connection and delete test database file
        try {
            dataSource.connection.close()
            java.io.File(testDbFile).delete()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Test
    fun `should store and retrieve Emby content`() {
        val content = RokuMediaContent(
            channelName = "Emby",
            channelId = "44191",
            contentId = "12345",
            title = "Inception",
            mediaType = "Movie"
        )

        val uuid = mediaStore.put("roku", content)
        val retrieved = mediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)

        assertNotNull(retrieved)
        assertEquals("Emby", retrieved!!.channelName)
        assertEquals("44191", retrieved.channelId)
        assertEquals("12345", retrieved.contentId)
        assertEquals("Inception", retrieved.title)
        assertEquals("Movie", retrieved.mediaType)
    }

    @Test
    fun `should store and retrieve Disney+ content`() {
        val content = RokuMediaContent(
            channelName = "Disney+",
            channelId = "291097",
            contentId = "f63db666-b097-4c61-99c1-b778de2d4ae1",
            title = "The Mandalorian",
            mediaType = "series"
        )

        val uuid = mediaStore.put("roku", content)
        val retrieved = mediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)

        assertNotNull(retrieved)
        assertEquals("Disney+", retrieved!!.channelName)
        assertEquals("291097", retrieved.channelId)
        assertEquals("f63db666-b097-4c61-99c1-b778de2d4ae1", retrieved.contentId)
    }

    @Test
    fun `should store and retrieve Netflix content`() {
        val content = RokuMediaContent(
            channelName = "Netflix",
            channelId = "12",
            contentId = "81444554",
            title = "Stranger Things",
            mediaType = "movie"
        )

        val uuid = mediaStore.put("roku", content)
        val retrieved = mediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)

        assertNotNull(retrieved)
        assertEquals("Netflix", retrieved!!.channelName)
        assertEquals("12", retrieved.channelId)
        assertEquals("81444554", retrieved.contentId)
    }

    @Test
    fun `should store and retrieve HBO Max content`() {
        val content = RokuMediaContent(
            channelName = "HBO Max",
            channelId = "61322",
            contentId = "urn:hbo:episode:abc123",
            title = "Game of Thrones S1E1",
            mediaType = "episode"
        )

        val uuid = mediaStore.put("roku", content)
        val retrieved = mediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)

        assertNotNull(retrieved)
        assertEquals("HBO Max", retrieved!!.channelName)
        assertEquals("61322", retrieved.channelId)
        assertEquals("urn:hbo:episode:abc123", retrieved.contentId)
    }

    @Test
    fun `should store and retrieve Prime Video content`() {
        val content = RokuMediaContent(
            channelName = "Prime Video",
            channelId = "13",
            contentId = "B0DKTFF815",
            title = "The Boys",
            mediaType = "movie"
        )

        val uuid = mediaStore.put("roku", content)
        val retrieved = mediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)

        assertNotNull(retrieved)
        assertEquals("Prime Video", retrieved!!.channelName)
        assertEquals("13", retrieved.channelId)
        assertEquals("B0DKTFF815", retrieved.contentId)
    }

    @Test
    fun `should store content with metadata`() {
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
        val retrieved = mediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)

        assertNotNull(retrieved)
        assertNotNull(retrieved!!.metadata)
        assertEquals(1800L, retrieved.metadata!!.resumePositionTicks)
        assertEquals("PG-13", retrieved.metadata!!.officialRating)
        assertEquals(2010, retrieved.metadata!!.year)
    }

    @Test
    fun `should update existing content`() {
        val originalContent = RokuMediaContent(
            channelName = "Netflix",
            channelId = "12",
            contentId = "81444554",
            title = "Original Title",
            mediaType = "movie"
        )

        val uuid = mediaStore.put("roku", originalContent)

        val updatedContent = RokuMediaContent(
            channelName = "Netflix",
            channelId = "12",
            contentId = "81444554",
            title = "Updated Title",
            mediaType = "movie"
        )

        mediaStore.update(uuid, "roku", updatedContent)
        val retrieved = mediaStore.getParsed(uuid, "roku", RokuMediaContent.Parser)

        assertNotNull(retrieved)
        assertEquals("Updated Title", retrieved!!.title)
    }

    @Test
    fun `should list all roku content`() {
        // Add multiple items
        val embyContent = RokuMediaContent(
            channelName = "Emby",
            channelId = "44191",
            contentId = "123",
            title = "Movie 1"
        )

        val netflixContent = RokuMediaContent(
            channelName = "Netflix",
            channelId = "12",
            contentId = "456",
            title = "Movie 2"
        )

        mediaStore.put("roku", embyContent)
        mediaStore.put("roku", netflixContent)

        val items = mediaStore.list(0, 10, "roku")

        assertEquals(2, items.size)
        assertEquals("roku", items[0].plugin)
        assertEquals("roku", items[1].plugin)
    }

    @Test
    fun `should count roku content`() {
        val content1 = RokuMediaContent(channelName = "Netflix", channelId = "12", contentId = "1", title = "Title 1")
        val content2 = RokuMediaContent(channelName = "Emby", channelId = "44191", contentId = "2", title = "Title 2")
        val content3 = RokuMediaContent(channelName = "Disney+", channelId = "291097", contentId = "3", title = "Title 3")

        mediaStore.put("roku", content1)
        mediaStore.put("roku", content2)
        mediaStore.put("roku", content3)

        val count = mediaStore.count("roku")
        assertEquals(3, count)
    }

    @Test
    fun `should remove content`() {
        val content = RokuMediaContent(
            channelName = "Netflix",
            channelId = "12",
            contentId = "123",
            title = "Test"
        )

        val uuid = mediaStore.put("roku", content)
        assertNotNull(mediaStore.get(uuid))

        mediaStore.remove(uuid)
        assertNull(mediaStore.get(uuid))
    }

    @Test
    fun `should handle pagination`() {
        // Add 5 items
        for (i in 1..5) {
            val content = RokuMediaContent(
                channelName = "Netflix",
                channelId = "12",
                contentId = "id$i",
                title = "Title $i"
            )
            mediaStore.put("roku", content)
        }

        // Get first page (2 items)
        val page1 = mediaStore.list(0, 2, "roku")
        assertEquals(2, page1.size)

        // Get second page (2 items)
        val page2 = mediaStore.list(2, 2, "roku")
        assertEquals(2, page2.size)

        // Get third page (1 item)
        val page3 = mediaStore.list(4, 2, "roku")
        assertEquals(1, page3.size)

        // Verify no duplicate UUIDs
        val allUuids = (page1 + page2 + page3).map { it.uuid }.toSet()
        assertEquals(5, allUuids.size)
    }
}
