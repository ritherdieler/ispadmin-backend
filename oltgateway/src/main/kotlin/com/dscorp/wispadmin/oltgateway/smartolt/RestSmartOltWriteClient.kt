package com.dscorp.wispadmin.oltgateway.smartolt

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets

@Component
class RestSmartOltWriteClient(
    @Value("\${olt.service.base-url}") private val baseUrl: String,
    @Value("\${olt.service.api-key}") private val apiKey: String,
    @Value("\${olt.service.connect-timeout-ms:5000}") connectTimeoutMs: Int,
    @Value("\${olt.service.read-timeout-ms:15000}") readTimeoutMs: Int,
) : SmartOltWriteClient {

    private val objectMapper = ObjectMapper()
    private val http = RestTemplate(SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(connectTimeoutMs)
        setReadTimeout(readTimeoutMs)
    })

    override fun authorize(command: SmartOltAuthorizeCommand): SmartOltWriteResult {
        val form = LinkedMultiValueMap<String, String>()
        form.add("olt_id", command.oltId)
        form.add("pon_type", command.ponType)
        form.add("board", command.board)
        form.add("port", command.port)
        form.add("sn", command.sn)
        form.add("vlan", command.vlan)
        form.add("onu_type", command.onuType)
        form.add("zone", command.zone)
        form.add("name", command.name)
        form.add("onu_mode", command.onuMode)
        form.add("custom_profile", command.customProfile)
        return post("onu/authorize_onu", form)
    }

    override fun delete(externalId: String): SmartOltWriteResult =
        post("onu/delete/${encode(externalId)}", null)

    override fun reboot(externalId: String): SmartOltWriteResult =
        post("onu/reboot/${encode(externalId)}", null)

    override fun move(sn: String, command: SmartOltMoveCommand): SmartOltWriteResult {
        val form = LinkedMultiValueMap<String, String>()
        form.add("olt_id", command.oltId)
        form.add("board", command.board)
        form.add("port", command.port)
        return post("onu/move/${encode(sn)}", form)
    }

    private fun post(path: String, form: MultiValueMap<String, String>?): SmartOltWriteResult {
        val headers = HttpHeaders()
        headers.set(TOKEN_HEADER, apiKey)
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        val entity = if (form == null) {
            HttpEntity<Void>(headers)
        } else {
            headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
            HttpEntity(form, headers)
        }
        val body = http.exchange(absoluteUrl(path), HttpMethod.POST, entity, String::class.java).body
        return parse(body)
    }

    private fun parse(body: String?): SmartOltWriteResult {
        val node = try {
            objectMapper.readTree(body ?: throw IllegalStateException("Empty SmartOLT response"))
        } catch (ex: IllegalStateException) {
            throw ex
        } catch (ex: Exception) {
            throw IllegalStateException("Invalid SmartOLT write response", ex)
        }
        if (!node.path("status").asBoolean(true)) {
            throw IllegalStateException(errorMessage(node))
        }
        val unique = node.get("unique_external_id")?.asText()?.takeIf { it.isNotBlank() }
        return SmartOltWriteResult(true, unique)
    }

    private fun errorMessage(node: JsonNode): String {
        val message = node.path("message").asText("").ifBlank { node.path("error").asText("") }
        return message.ifBlank { "SmartOLT write failed" }
    }

    private fun absoluteUrl(path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private fun encode(value: String): String =
        UriUtils.encodePathSegment(value, StandardCharsets.UTF_8)

    private companion object {
        const val TOKEN_HEADER = "X-Token"
    }
}
