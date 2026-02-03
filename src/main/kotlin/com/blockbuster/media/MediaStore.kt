package com.blockbuster.media

import java.time.Instant

interface MediaStore {
    /**
     * Create a new item for the plugin with generated UUID; returns the UUID
     */
    fun put(
        plugin: String,
        content: MediaContent,
    ): String

    /**
     * Update (or upsert) an item at a specific UUID for the plugin
     */
    fun update(
        uuid: String,
        plugin: String,
        content: MediaContent,
    )

    /**
     * Get stored item metadata and raw JSON by UUID
     */
    fun get(uuid: String): MediaItem?

    /**
     * Get and parse JSON content into a strongly typed object, validating plugin
     */
    fun <T : MediaContent> getParsed(
        uuid: String,
        plugin: String,
        parser: MediaContentParser<T>,
    ): T?

    /**
     * Remove content by UUID
     */
    fun remove(uuid: String)

    /**
     * List items with pagination and optional plugin filter
     */
    fun list(
        offset: Int,
        limit: Int,
        plugin: String? = null,
    ): List<MediaItem>

    /**
     * Count items, optionally filtered by plugin
     */
    fun count(plugin: String? = null): Int
}

data class MediaItem(
    val uuid: String,
    val plugin: String,
    val configJson: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
