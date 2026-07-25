package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

@Service
class WhatsAppMediaDownloadService(
    private val whatsAppProperties: WhatsAppProperties,
    tracingInterceptor: com.dscorp.wispadmin.observability.tracing.TracingClientHttpRequestInterceptor
) {

    private val log = LoggerFactory.getLogger(WhatsAppMediaDownloadService::class.java)
    private val restTemplate = RestTemplate().apply { interceptors.add(tracingInterceptor) }

    fun downloadAndStore(mediaId: String, mimeType: String?): String? {
        if (!whatsAppProperties.isConfigured()) return null
        val url = fetchMediaUrl(mediaId) ?: return null
        val bytes = fetchMediaBytes(url) ?: return null
        return storeLocally(mediaId, mimeType, bytes)
    }

    fun resolveStoredPath(storedPath: String?): Path? {
        if (storedPath.isNullOrBlank()) return null
        val path = Paths.get(storedPath)
        return if (Files.exists(path)) path else null
    }

    private fun fetchMediaUrl(mediaId: String): String? {
        val headers = authHeaders()
        return try {
            val response = restTemplate.exchange(
                "${whatsAppProperties.graphApiBaseUrl()}/$mediaId",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                Map::class.java
            )
            response.body?.get("url")?.toString()
        } catch (e: Exception) {
            log.warn("Media: no se pudo obtener URL para $mediaId: ${e.message}")
            null
        }
    }

    private fun fetchMediaBytes(url: String): ByteArray? {
        val headers = authHeaders()
        return try {
            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                ByteArray::class.java
            )
            response.body
        } catch (e: Exception) {
            log.warn("Media: no se pudo descargar contenido: ${e.message}")
            null
        }
    }

    private fun storeLocally(mediaId: String, mimeType: String?, bytes: ByteArray): String {
        val dir = Paths.get(whatsAppProperties.mediaStorageDir)
        Files.createDirectories(dir)
        val extension = extensionForMime(mimeType)
        val filename = "${mediaId}_${UUID.randomUUID()}$extension"
        val target = dir.resolve(filename)
        Files.write(target, bytes)
        return target.toAbsolutePath().toString()
    }

    private fun authHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.setBearerAuth(whatsAppProperties.accessToken.trim())
        return headers
    }

    private fun extensionForMime(mimeType: String?): String {
        return when (mimeType?.lowercase()) {
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "application/pdf" -> ".pdf"
            else -> ".bin"
        }
    }
}
