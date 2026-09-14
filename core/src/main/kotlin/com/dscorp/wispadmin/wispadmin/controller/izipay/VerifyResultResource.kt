package com.dscorp.wispadmin.wispadmin.controller.izipay

import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.service.MikrotikService
import com.dscorp.wispadmin.wispadmin.util.ServerConfiguration
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.lyra.rest.client.Client
import com.lyra.rest.client.ClientException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController


/**
 * Rest controller that allows check the integrity of the server answer
 *
 * @author Lyra Network
 */
@RestController
@RequestMapping("/izipay")
class VerifyResultResource @Autowired constructor(
    private val configuration: ServerConfiguration,
    private val mikrotikService: MikrotikService
) {
    private val logger = LoggerFactory.getLogger(VerifyResultResource::class.java)

    @RequestMapping(path = ["/verifyResult"], method = [RequestMethod.POST])
    fun verifyResult(@RequestBody payload: Map<String?, Any?>): ResponseEntity<String> {
        logger.info("verifyResult called")
        logger.info("Request data: " + payload.entries.toTypedArray().contentToString())
        val isValid: Boolean = try {

            // Obtener el valor del campo kr-answer como una cadena JSON
            val krAnswerJson = payload["kr-answer"] as String?
            // Analizar la cadena JSON kr-answer en un mapa

            // Analizar la cadena JSON kr-answer en un mapa
            val krAnswerMap: Map<String?, Any?>? =
                ObjectMapper().readValue(krAnswerJson, object : TypeReference<Map<String?, Any?>?>() {})

            // Obtener el valor del atributo orderId
            val orderId = (krAnswerMap!!["orderDetails"] as Map<String?, Any?>?)!!["orderId"] as String?


            mikrotikService.savePaymentWithCard(orderId!!.toInt())

            Client.verifyAnswer(payload, configuration.getConfiguration())

        } catch (lce: ClientException) {
            logger.error("Error when calling verifyResult", lce)
            return ResponseEntity(lce.responseMessage + "-" + lce.cause, HttpStatus.INTERNAL_SERVER_ERROR)
        }
        logger.info("Answer integrity is valid?: $isValid")
        return if (isValid) {
            ResponseEntity("{\"isAnswerIntegrityValid\" : \"true\"}", HttpStatus.OK)
        } else {
            ResponseEntity("{\"isAnswerIntegrityValid\" : \"false\"}", HttpStatus.BAD_REQUEST)
        }
    }
}
