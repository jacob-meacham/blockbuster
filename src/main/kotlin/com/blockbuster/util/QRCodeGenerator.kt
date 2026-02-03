package com.blockbuster.util

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.j2se.MatrixToImageWriter
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Utility for generating QR codes from URLs.
 */
object QRCodeGenerator {
    /**
     * Generate a base64-encoded PNG QR code from a URL.
     *
     * @param url The URL to encode in the QR code
     * @param size The size of the QR code image in pixels (default: 300x300)
     * @return Base64-encoded PNG image data
     */
    fun generateBase64(url: String, size: Int = 300): String {
        val bitMatrix = MultiFormatWriter().encode(
            url,
            BarcodeFormat.QR_CODE,
            size,
            size
        )
        val bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix)
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(bufferedImage, "PNG", outputStream)
        return Base64.getEncoder().encodeToString(outputStream.toByteArray())
    }
}
