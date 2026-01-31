package com.blockbuster.media

import org.slf4j.LoggerFactory
import java.sql.SQLException
import javax.sql.DataSource

class SqliteMediaStore(
    private val dataSource: DataSource
) : MediaStore {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun put(plugin: String, content: MediaContent): String {
        val uuid = java.util.UUID.randomUUID().toString()
        val configJson = content.toJson()
        try {
            dataSource.connection.use { connection ->
                val insert = """
                    INSERT INTO media_library (uuid, plugin, config_json)
                    VALUES (?, ?, ?)
                """.trimIndent()
                connection.prepareStatement(insert).use { ps ->
                    ps.setString(1, uuid)
                    ps.setString(2, plugin)
                    ps.setString(3, configJson)
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to put media item: ${e.message}", e)
            throw e
        }
        return uuid
    }

    override fun update(uuid: String, plugin: String, content: MediaContent) {
        val configJson = content.toJson()
        try {
            dataSource.connection.use { connection ->
                val existsSql = "SELECT 1 FROM media_library WHERE uuid = ?"
                val exists = connection.prepareStatement(existsSql).use { ps ->
                    ps.setString(1, uuid)
                    ps.executeQuery().next()
                }

                if (exists) {
                    val update = """
                        UPDATE media_library
                        SET plugin = ?, config_json = ?, updated_at = strftime('%Y-%m-%d %H:%M:%f', 'now')
                        WHERE uuid = ?
                    """.trimIndent()
                    connection.prepareStatement(update).use { ps ->
                        ps.setString(1, plugin)
                        ps.setString(2, configJson)
                        ps.setString(3, uuid)
                        ps.executeUpdate()
                    }
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to update media item: ${e.message}", e)
            throw e
        }
    }

    override fun get(uuid: String): MediaItem? {
        val sql = "SELECT uuid, plugin, config_json, created_at, updated_at FROM media_library WHERE uuid = ?"
        return try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { ps ->
                    ps.setString(1, uuid)
                    val rs = ps.executeQuery()
                    if (rs.next()) {
                        MediaItem(
                            uuid = rs.getString("uuid"),
                            plugin = rs.getString("plugin"),
                            configJson = rs.getString("config_json"),
                            createdAt = rs.getTimestamp("created_at").toInstant(),
                            updatedAt = rs.getTimestamp("updated_at").toInstant()
                        )
                    } else null
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to get media item: ${e.message}", e)
            null
        }
    }

    override fun <T : MediaContent> getParsed(uuid: String, plugin: String, parser: MediaContentParser<T>): T? {
        val item = get(uuid) ?: return null
        if (item.plugin != plugin) {
            throw IllegalArgumentException("Plugin mismatch for uuid=$uuid: expected '$plugin' but was '${item.plugin}'")
        }
        return try {
            parser.fromJson(item.configJson)
        } catch (e: Exception) {
            logger.error("Failed to parse content for uuid=$uuid plugin=$plugin: ${e.message}", e)
            throw e
        }
    }

    override fun remove(uuid: String) {
        val sql = "DELETE FROM media_library WHERE uuid = ?"
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { ps ->
                    ps.setString(1, uuid)
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to remove media item: ${e.message}", e)
            throw e
        }
    }

    override fun list(offset: Int, limit: Int, plugin: String?): List<MediaItem> {
        val where = plugin?.takeIf { it.isNotBlank() }
            ?.let { "WHERE plugin = ?" }
            .orEmpty()
        
        val sql = """
            SELECT uuid, plugin, config_json, created_at, updated_at
            FROM media_library
            $where
            ORDER BY updated_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val items = mutableListOf<MediaItem>()
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { ps ->
                    var idx = 1
                    if (!plugin.isNullOrBlank()) ps.setString(idx++, plugin)
                    ps.setInt(idx++, limit)
                    ps.setInt(idx, offset)

                    val rs = ps.executeQuery()
                    while (rs.next()) {
                        items += MediaItem(
                            uuid = rs.getString("uuid"),
                            plugin = rs.getString("plugin"),
                            configJson = rs.getString("config_json"),
                            createdAt = rs.getTimestamp("created_at").toInstant(),
                            updatedAt = rs.getTimestamp("updated_at").toInstant()
                        )
                    }
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to list media items: ${e.message}", e)
        }
        return items
    }

    override fun count(plugin: String?): Int {
        val where = plugin?.takeIf { it.isNotBlank() }
            ?.let { "WHERE plugin = ?" }
            .orEmpty()

        val sql = """
            SELECT COUNT(1) AS cnt
            FROM media_library
            $where
        """.trimIndent()

        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { ps ->
                    if (!plugin.isNullOrBlank()) ps.setString(1, plugin)
                    val rs = ps.executeQuery()
                    if (rs.next()) return rs.getInt("cnt")
                }
            }
        } catch (e: SQLException) {
            logger.error("Failed to count media items: ${e.message}", e)
        }
        return 0
    }
}
