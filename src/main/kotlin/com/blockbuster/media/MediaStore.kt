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
     * Store content, creating a new entry or updating an existing one.
     * If [uuid] is null, creates a new entry and returns the generated UUID.
     * If [uuid] is non-null, updates the existing entry and returns the same UUID.
     *
     * @return the UUID of the stored item
     */
    fun putOrUpdate(
        uuid: String?,
        plugin: String,
        content: MediaContent,
    ): String

    /**
     * Remove content by UUID.
     * @return true if an item was deleted, false if no item existed with that UUID
     */
    fun remove(uuid: String): Boolean

    /**
     * Rename a library item by updating the "title" field in its stored JSON.
     *
     * @param uuid the item's UUID
     * @param newTitle the new title (must not be blank)
     * @return true if the item was found and renamed, false if no item exists with that UUID
     * @throws IllegalArgumentException if [newTitle] is blank
     */
    fun rename(
        uuid: String,
        newTitle: String,
    ): Boolean

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
