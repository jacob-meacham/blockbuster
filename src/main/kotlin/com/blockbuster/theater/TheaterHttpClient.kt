package com.blockbuster.theater

import org.slf4j.LoggerFactory
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Sends HTTP requests and validates responses for theater device handlers.
 */
interface TheaterHttpClient {
    /**
     * Send an HTTP request and validate the response.
     *
     * @param request The HTTP request to send
     * @param deviceType Human-readable device type for error messages
     * @throws TheaterSetupException if the response is non-2xx
     */
    fun sendAndCheck(request: HttpRequest, deviceType: String)
}

/**
 * Default [TheaterHttpClient] backed by [java.net.http.HttpClient].
 *
 * Throws [TheaterSetupException] on non-2xx responses.
 */
class DefaultTheaterHttpClient(private val httpClient: HttpClient) : TheaterHttpClient {

    private val logger = LoggerFactory.getLogger(DefaultTheaterHttpClient::class.java)

    override fun sendAndCheck(request: HttpRequest, deviceType: String) {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val status = response.statusCode()
        logger.debug("{} response: {}", deviceType, status)
        if (status !in 200..299) {
            throw TheaterSetupException(deviceType, "HTTP $status: ${response.body()}")
        }
    }
}
