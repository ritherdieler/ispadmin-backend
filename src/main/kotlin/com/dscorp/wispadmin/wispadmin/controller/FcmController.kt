package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.FcmToken
import com.dscorp.wispadmin.wispadmin.repository.FcmTokenRepository
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmMessage
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.gson.Gson
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/fcm")
class FcmController @Autowired constructor(
    private val fcm: FirebaseMessaging,
    private val repository: FcmTokenRepository
) {

    val objectErrorResponse: ResponseEntity<FcmToken> = ResponseEntity.status(500).body(null)
    val listObjectErrorResponse: ResponseEntity<List<FcmToken>> = ResponseEntity.status(500).body(null)


    @PostMapping("save-token")
    fun registerFcmToken(@RequestBody newFcmToken: FcmToken): ResponseEntity<FcmToken> {
        return try {
            val fcmToken = repository.save(newFcmToken)
            ResponseEntity.status(200).body(fcmToken)
        } catch (e: Exception) {
            e.printStackTrace()
            objectErrorResponse
        }
    }


    @GetMapping("get-token")
    fun getFcmToken(@RequestParam subscriptionId: Int): ResponseEntity<FcmToken> {
        return try {
            val token = repository.findById(subscriptionId).get()
            ResponseEntity.status(200).body(token)
        } catch (e: Exception) {
            e.printStackTrace()
            objectErrorResponse
        }
    }

    @PostMapping("sendNotification/{registrationToken}")
    @Throws(FirebaseMessagingException::class)
    fun postToClient(
        @RequestBody fcmMessage: FcmMessage,
        @PathVariable("registrationToken") registrationToken: String
    ): ResponseEntity<String?>? {


        val messageAsJson = Gson().toJson(fcmMessage)

        val msg: Message = Message.builder()
            .setToken(registrationToken)
            .putData("body", messageAsJson)
            .build()
        val id: String = fcm.send(msg)
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body<String>(id)
    }

}

