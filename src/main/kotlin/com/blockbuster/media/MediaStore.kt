package com.blockbuster.media

interface MediaStore<T> {
    
    /**
     * Get media content by its UUID
     */
    fun getMediaContent(uuid: String): T?
    
    /**
     * Store media content with a UUID
     */
    fun storeMediaContent(uuid: String, content: T)
    
    /**
     * Remove media content by UUID
     */
    fun removeMediaContent(uuid: String)
}

/**
 * Entry in the media store with UUID
 */
data class MediaContentEntry<T>(
    val uuid: String,
    val content: T
)
