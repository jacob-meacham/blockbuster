package com.blockbuster.media

import java.sql.ResultSet
import java.sql.SQLException
import java.time.Instant
import org.slf4j.LoggerFactory
import javax.sql.DataSource

data class RokuMediaContent(
    val channelName: String? = null,
    val channelId: String,
    val ecpCommand: String = "launch",
    val contentId: String,
    val mediaType: String? = null,
    val title: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

interface RokuMediaStore : MediaStore<RokuMediaContent> { }

class SqliteRokuMediaStore(
    private val dataSource: DataSource
) : RokuMediaStore {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    init {
        // Ensure the database file exists and is accessible
        try {
            dataSource.connection.use { connection ->
                // Test the connection
                connection.createStatement().execute("SELECT 1")
                logger.info("SQLite database initialized successfully")
            }
        } catch (e: SQLException) {
            logger.error("Failed to initialize SQLite database: ${e.message}", e)
            throw RuntimeException("Database initialization failed", e)
        }
    }
    
    override fun getMediaContent(uuid: String): RokuMediaContent? {
        val sql = """
            SELECT uuid, channel_id, ecp_command, content_id, media_type, title, channel_name, created_at, updated_at
            FROM roku_media 
            WHERE uuid = ?
        """.trimIndent()
        
        return try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, uuid)
                    val rs = stmt.executeQuery()
                    
                    if (rs.next()) {
                        mapResultSetToRokuMediaContent(rs)
                    } else {
                        null
                    }
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to get media content for UUID $uuid: ${e.message}", e)
            null
        }
    }
    
    override fun storeMediaContent(uuid: String, content: RokuMediaContent) {
        try {
            dataSource.connection.use { connection ->
                // Check if record exists
                val checkSql = "SELECT 1 FROM roku_media WHERE uuid = ?"
                val exists = connection.prepareStatement(checkSql).use { stmt ->
                    stmt.setString(1, uuid)
                    val rs = stmt.executeQuery()
                    rs.next()
                }
                
                if (exists) {
                    // Update existing record
                    val updateSql = """
                        UPDATE roku_media 
                        SET channel_id = ?, ecp_command = ?, content_id = ?, media_type = ?, title = ?, channel_name = ?, updated_at = strftime('%Y-%m-%d %H:%M:%f', 'now')
                        WHERE uuid = ?
                    """.trimIndent()
                    
                    connection.prepareStatement(updateSql).use { stmt ->
                        stmt.setString(1, content.channelId)
                        stmt.setString(2, content.ecpCommand)
                        stmt.setString(3, content.contentId)
                        stmt.setString(4, content.mediaType)
                        stmt.setString(5, content.title)
                        stmt.setString(6, content.channelName)
                        stmt.setString(7, uuid)
                        
                        stmt.executeUpdate()
                        logger.debug("Updated media content for UUID: $uuid")
                    }
                } else {
                    // Insert new record
                    val insertSql = """
                        INSERT INTO roku_media 
                        (uuid, channel_id, ecp_command, content_id, media_type, title, channel_name)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                    
                    connection.prepareStatement(insertSql).use { stmt ->
                        stmt.setString(1, uuid)
                        stmt.setString(2, content.channelId)
                        stmt.setString(3, content.ecpCommand)
                        stmt.setString(4, content.contentId)
                        stmt.setString(5, content.mediaType)
                        stmt.setString(6, content.title)
                        stmt.setString(7, content.channelName)
                        
                        stmt.executeUpdate()
                        logger.debug("Inserted new media content for UUID: $uuid")
                    }
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to store media content for UUID $uuid: ${e.message}", e)
            throw SQLException("Failed to store media content", e)
        }
    }
    
    override fun removeMediaContent(uuid: String) {
        val sql = "DELETE FROM roku_media WHERE uuid = ?"
        
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, uuid)
                    val rowsAffected = stmt.executeUpdate()
                    
                    if (rowsAffected > 0) {
                        logger.debug("Removed media content for UUID: $uuid")
                    } else {
                        logger.warn("No media content found to remove for UUID: $uuid")
                    }
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to remove media content for UUID $uuid: ${e.message}", e)
            throw SQLException("Failed to remove media content", e)
        }
    }
    
    /**
     * Map a ResultSet to RokuMediaContent
     */
    private fun mapResultSetToRokuMediaContent(rs: ResultSet): RokuMediaContent {
        val createdAt = rs.getTimestamp("created_at")?.toInstant() ?: Instant.now()
        val updatedAt = rs.getTimestamp("updated_at")?.toInstant() ?: Instant.now()
        
        logger.debug("Parsed timestamps - created_at: $createdAt, updated_at: $updatedAt")
        
        return RokuMediaContent(
            channelName = rs.getString("channel_name"),
            channelId = rs.getString("channel_id"),
            ecpCommand = rs.getString("ecp_command") ?: "launch",
            contentId = rs.getString("content_id"),
            mediaType = rs.getString("media_type"),
            title = rs.getString("title"),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
    
    /**
     * Close the database connection
     */
    fun close() {
        try {
            dataSource.connection.close()
            logger.info("SQLite database connection closed")
        } catch (e: SQLException) {
            logger.warn("Error closing database connection: ${e.message}")
        }
    }
}
