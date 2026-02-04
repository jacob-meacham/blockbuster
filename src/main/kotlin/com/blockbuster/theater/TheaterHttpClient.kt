package com.blockbuster.theater

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory

/**
 * Sends HTTP requests and validates responses for theater device handlers.
 */
interface TheaterHttpClient {
    /**
     * Send an HTTP POST request and validate the response.
     *
     * @param url The URL to POST to
     * @param body The JSON request body, or null for an empty body
     * @param deviceType Human-readable device type for error messages
     * @throws TheaterSetupException if the response is non-2xx
     */
    fun sendAndCheck(
        url: String,
        body: String?,
        deviceType: String,
    )
}

/**
 * Default [TheaterHttpClient] backed by [OkHttpClient].
 *
 * Throws [TheaterSetupException] on non-2xx responses.
 */
class DefaultTheaterHttpClient(private val httpClient: OkHttpClient) : TheaterHttpClient {
    private val logger = LoggerFactory.getLogger(DefaultTheaterHttpClient::class.java)

    override fun sendAndCheck(
        url: String,
        body: String?,
        deviceType: String,
    ) {
        val requestBody = (body ?: "").toRequestBody(JSON_MEDIA_TYPE)
        val request =
            Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

        val response = httpClient.newCall(request).execute()
        val status = response.code
        logger.debug("{} response: {}", deviceType, status)
        if (status !in 200..299) {
            throw TheaterSetupException(deviceType, "HTTP $status: ${response.body?.string()}")
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
