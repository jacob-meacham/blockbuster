package com.blockbuster.plugin.roku

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Cross-channel parameterized tests for URL extraction.
 * Validates that all streaming channel plugins correctly extract content IDs from URLs.
 */
class UrlExtractionParameterizedTest {
    companion object {
        private val netflix = NetflixRokuChannelPlugin()
        private val disney = DisneyPlusRokuChannelPlugin()
        private val hbo = HBOMaxRokuChannelPlugin()
        private val prime = PrimeVideoRokuChannelPlugin()

        @JvmStatic
        fun validUrls(): Stream<Arguments> =
            Stream.of(
                // Netflix
                Arguments.of(netflix, "https://www.netflix.com/watch/81444554", "81444554", "movie"),
                Arguments.of(netflix, "https://netflix.com/watch/12345", "12345", "movie"),
                Arguments.of(netflix, "https://www.netflix.com/title/80179766", "80179766", "series"),
                // Disney+
                Arguments.of(
                    disney,
                    "https://www.disneyplus.com/play/f63db666-b097-4c61-99c1-b778de2d4ae1",
                    "f63db666-b097-4c61-99c1-b778de2d4ae1",
                    "movie",
                ),
                Arguments.of(disney, "https://disneyplus.com/video/abc-123-def", "abc-123-def", "movie"),
                // HBO Max
                Arguments.of(
                    hbo,
                    "https://www.max.com/video/watch/bd43b2a4-1639-4197-96d4-2ec14eb45e9e",
                    "bd43b2a4-1639-4197-96d4-2ec14eb45e9e",
                    "movie",
                ),
                Arguments.of(hbo, "https://max.com/play/some-show-id", "some-show-id", "movie"),
                Arguments.of(hbo, "https://www.hbomax.com/video/watch/legacy-id", "legacy-id", "movie"),
                // Prime Video
                Arguments.of(prime, "https://www.amazon.com/gp/video/detail/B0DKTFF815", "B0DKTFF815", "movie"),
                Arguments.of(prime, "https://amazon.com/gp/video/detail/B0FQM41JFJ/ref=xyz", "B0FQM41JFJ", "movie"),
                Arguments.of(prime, "https://amazon.com/dp/B0FQM41JFJ/ref=xyz", "B0FQM41JFJ", "movie"),
                Arguments.of(prime, "https://www.primevideo.com/detail/B0EXAMPL12", "B0EXAMPL12", "movie"),
            )

        @JvmStatic
        fun invalidUrls(): Stream<Arguments> =
            Stream.of(
                // Netflix
                Arguments.of(netflix, "https://netflix.com/browse"),
                Arguments.of(netflix, "https://netflix.com/"),
                Arguments.of(netflix, "https://www.netflix.com/search?q=test"),
                Arguments.of(netflix, "https://disneyplus.com/play/abc-123"),
                // Disney+
                Arguments.of(disney, "https://disneyplus.com/browse"),
                Arguments.of(disney, "https://disneyplus.com/"),
                Arguments.of(disney, "https://netflix.com/watch/12345"),
                // HBO Max
                Arguments.of(hbo, "https://max.com/browse"),
                Arguments.of(hbo, "https://max.com/"),
                Arguments.of(hbo, "https://netflix.com/watch/12345"),
                // Prime Video
                Arguments.of(prime, "https://amazon.com/browse"),
                Arguments.of(prime, "https://www.amazon.com/"),
                Arguments.of(prime, "https://netflix.com/watch/12345"),
            )
    }

    @ParameterizedTest(name = "{1} -> contentId={2}")
    @MethodSource("validUrls")
    fun `extractFromUrl extracts content from valid URLs`(
        plugin: StreamingRokuChannelPlugin,
        url: String,
        expectedContentId: String,
        expectedMediaType: String,
    ) {
        val content = plugin.extractFromUrl(url)

        assertNotNull(content, "Expected non-null content for URL: $url")
        assertEquals(expectedContentId, content!!.contentId, "Content ID mismatch for URL: $url")
        assertEquals(expectedMediaType, content.mediaType, "Media type mismatch for URL: $url")
        assertNotNull(content.channelName, "Channel name should not be null for URL: $url")
        assertNotNull(content.channelId, "Channel ID should not be null for URL: $url")
    }

    @ParameterizedTest(name = "{1} -> null")
    @MethodSource("invalidUrls")
    fun `extractFromUrl returns null for invalid URLs`(
        plugin: StreamingRokuChannelPlugin,
        url: String,
    ) {
        val content = plugin.extractFromUrl(url)
        assertNull(content, "Expected null for URL: $url")
    }
}
