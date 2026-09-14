package com.dscorp.wispadmin.wispadmin.controller.izipay

import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.requestbody.CreatePaymentIziRequest
import com.dscorp.wispadmin.wispadmin.util.ServerConfiguration
import com.lyra.rest.client.Client
import com.lyra.rest.client.ClientException
import com.lyra.rest.client.ClientResource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

/**
 * Rest controller that allows to invoke the payment platform in order to create a payment or token instance
 * and return all the details in JSON format.
 *
 *
 * The information retrieved should be immediately provided to mobile SDK.
 *
 * @author Lyra Network
 */
@RestController
@RequestMapping("/izipay")
class CreateResource @Autowired constructor(
    private val configuration: ServerConfiguration,
    private val paymentRepository: PaymentRepository

) {
    private val logger = LoggerFactory.getLogger(CreateResource::class.java)

    /**
     * Uses client API in order to create a payment and returns the formToken associated with payment session
     * that should be provided to client mobile SDK
     *
     * @param payload the payment session input data
     * @return ResponseEntity<String> JSON string containing the formToken
    </String> */
    @RequestMapping(path = ["/createPayment"], method = [RequestMethod.POST])
    fun createPayment(@RequestBody payload: CreatePaymentIziRequest): ResponseEntity<String> {
        logger.info("createPayment called")
        logger.info("Request data: {}", payload.toString())
        return if (!validatePayloadData(payload)) {
            ResponseEntity(
                "Server cannot process this request because it is invalid",
                HttpStatus.BAD_REQUEST
            )
        } else {
//           val result =  executeOperation(ClientResource.CREATE_PAYMENT.toString(), payload.getPaymentParams())
//            updateLocalPayment(payload)
//            return result
            return           executeOperation(ClientResource.CREATE_PAYMENT.toString(), payload.getPaymentParams())

        }

        //Handle errors here
    }

    private fun updateLocalPayment(payload: CreatePaymentIziRequest) {
        paymentRepository.findById(payload.orderId.toInt()).get().apply {
            this.paid = true
            this.method = "card-app"
            paymentRepository.save(this)
        }
    }

    /**
     * Uses client API in order to create a payment by token and returns the formToken associated with payment session
     * that should be provided to client mobile SDK
     *
     * @param payload the payment session input data
     * @return ResponseEntity<String> JSON string containing the formToken
    </String> */
    @RequestMapping(path = ["/createToken"], method = [RequestMethod.POST])
    fun createRegister(@RequestBody payload: CreatePaymentIziRequest): ResponseEntity<String> {
        logger.info("createToken called")
        logger.info("Request data: {}", payload.toString())
        return if (!validatePayloadData(payload)) {
            ResponseEntity(
                "Server cannot process this request because it is invalid",
                HttpStatus.BAD_REQUEST
            )
        } else executeOperation(ClientResource.CREATE_TOKEN.toString(), payload.getPaymentParams())

        //Handle errors here
    }

    /*
     * Method that uses the client library in order to call the requested operation of the payment Rest API
     */
    private fun executeOperation(operation: String, payload: Map<String, Any>): ResponseEntity<String> {
        val responseMessage: String = try {
            Client.post(operation, payload, configuration.getConfiguration())
        } catch (lce: ClientException) {
            logger.error("Error when calling $operation", lce)
            //Check exception and sent code and message if possible
            return if (lce.responseCode > 0) {
                ResponseEntity(lce.responseMessage, HttpStatus.valueOf(lce.responseCode))
            } else {
                ResponseEntity(lce.responseMessage + "-" + lce.cause, HttpStatus.INTERNAL_SERVER_ERROR)
            }
        }
        logger.info("Response OK: $responseMessage")
        return ResponseEntity(responseMessage, HttpStatus.OK)
    }

    /*
     * Checks sent data in order to detect incoherence with real payment data: amount, currency
     *
     * FIXME: this method should be implemented in order to avoid attacks that modify the payload content
     */
    private fun validatePayloadData(payload: CreatePaymentIziRequest): Boolean {
        return try {
            //check if the payment exists locally
            val localPaymentReference = paymentRepository.findById(payload.orderId.toInt()).get()
            //check if the amount is the same
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

private fun CreatePaymentIziRequest.getPaymentParams(): Map<String, Any> {
    val params = mutableMapOf(
        "currency" to currency,
        "amount" to amount,
        "orderId" to orderId,
        "customer" to mapOf(
            "email" to customer.email,
            "reference" to customer.reference,
        ),
        "formTokenVersion" to formTokenVersion,
        "mode" to mode,
    )
    if (formAction != null) {
        params["formAction"] = "ASK_REGISTER_PAY"
    }
    return params
}
