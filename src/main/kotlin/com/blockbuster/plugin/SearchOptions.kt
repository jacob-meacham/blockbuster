package com.blockbuster.plugin

/**
 * Type-safe search options for plugin search operations.
 */
data class SearchOptions(
    val limit: Int = DEFAULT_SEARCH_LIMIT,
    val offset: Int = 0
) {
    companion object {
        const val DEFAULT_SEARCH_LIMIT = 20
    }
}
