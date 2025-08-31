package com.blockbuster.plugin

import com.blockbuster.media.RokuMediaContent
import org.slf4j.LoggerFactory
import com.blockbuster.media.RokuMediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Roku plugin for controlling Roku devices via ECP (External Control Protocol).
 * Uses basic HTTP requests to communicate with Roku devices.
 * 
 * ECP Command Format:
 * http://<roku-device-ip-address>:8060/<EcpCommand>/<channelId>?contentId=<contentIdValue>&mediaType=<mediaTypeValue>
 */
class RokuPlugin(
    private val deviceIp: String,
    private val deviceName: String = "Roku Device",
    private val rokuMediaStore: RokuMediaStore,
    private val httpClient: OkHttpClient
) : MediaPlugin {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val ecpPort = 8060
    private val ecpProtocol = "http"

    override fun getPluginName(): String = "roku"
    
    override fun getDescription(): String = "Controls Roku devices via ECP protocol"


    @Throws(PluginException::class)
    override fun play(contentId: String, options: Map<String, Any>) {
        try {
            val content = rokuMediaStore.getMediaContent(contentId)
            if (content == null) {
                throw PluginException("Content not found in media store")
            }
            
            val url = buildEcpUrl(content.ecpCommand, content.channelId, content.contentId, content.mediaType)
            sendEcpRequest(url, "POST")
            
            logger.info("Successfully executed Roku ECP command: $url")
            
        } catch (e: Exception) {
            logger.error("Failed to execute Roku command '$contentId': ${e.message}", e)
            throw PluginException("Failed to execute Roku command: ${e.message}", e)
        }
    }
    
    /**
     * Build the ECP URL according to the format:
     * http://<ip>:8060/<ecpCommand>/<channelId>?contentId=<contentIdValue>&mediaType=<mediaTypeValue>
     */
    private fun buildEcpUrl(ecpCommand: String, channelId: String, contentId: String?, mediaType: String?): String {
        val baseUrl = "$ecpProtocol://$deviceIp:$ecpPort/$ecpCommand/$channelId"
        
        val queryParams = mutableListOf<String>()
        if (!contentId.isNullOrBlank()) {
            queryParams.add("contentId=$contentId")
        }
        if (!mediaType.isNullOrBlank()) {
            queryParams.add("mediaType=$mediaType")
        }
        
        return if (queryParams.isNotEmpty()) {
            "$baseUrl?${queryParams.joinToString("&")}"
        } else {
            baseUrl
        }
    }
    
    /**
     * Send a key press to the Roku device
     */
    fun sendKeyPress(key: String) {
        val url = "$ecpProtocol://$deviceIp:$ecpPort/keypress/$key"
        sendEcpRequest(url, "POST")
    }
    
    /**
     * Get device information
     */
    fun getDeviceInfo(): String? {
        val url = "$ecpProtocol://$deviceIp:$ecpPort/query/device-info"
        return sendEcpRequest(url, "GET")
    }
    
    /**
     * Send an ECP request to the Roku device
     */
    private fun sendEcpRequest(urlString: String, method: String): String? {
        return try {
            val requestBuilder = Request.Builder().url(urlString)
            
            when (method.uppercase()) {
                "GET" -> requestBuilder.get()
                "POST" -> requestBuilder.post("".toRequestBody())
                else -> throw IllegalArgumentException("Unsupported HTTP method: $method")
            }
            
            val request = requestBuilder.build()
            val response = httpClient.newCall(request).execute()
            
            val responseBody = response.body?.string()
            
            if (response.isSuccessful) {
                logger.debug("ECP request successful: $urlString, response: $responseBody")
                responseBody
            } else {
                logger.error("ECP request failed: $urlString, response code: ${response.code}")
                throw PluginException("ECP request failed with response code: ${response.code}")
            }
        } catch (e: Exception) {
            logger.error("Failed to send ECP request to $urlString: ${e.message}", e)
            throw PluginException("Failed to send ECP request: ${e.message}", e)
        }
    }
}
