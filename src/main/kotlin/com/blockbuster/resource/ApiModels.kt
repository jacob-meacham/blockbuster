package com.blockbuster.resource

data class SearchAllResponse(
    val query: String,
    val totalResults: Int,
    val results: List<SearchResultItem>,
)

data class SearchResultItem(
    val source: String?,
    val plugin: String,
    val title: String,
    val url: String?,
    val description: String?,
    val imageUrl: String?,
    val dedupKey: String?,
    val content: Any?,
)

data class SearchPluginResponse(
    val plugin: String,
    val query: String,
    val totalResults: Int,
    val results: List<com.blockbuster.plugin.SearchResult<*>>,
)

data class PluginListResponse(
    val plugins: List<PluginInfo>,
)

data class PluginInfo(
    val name: String,
    val description: String,
)

data class ChannelListResponse(
    val channels: List<com.blockbuster.plugin.ChannelInfoItem>,
)

data class ErrorResponse(
    val error: String?,
)
