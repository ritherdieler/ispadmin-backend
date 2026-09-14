package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.FcmTokenRequestDto
import com.dscorp.wispadmin.wispadmin.dto.FcmTokenResponseDto
import com.dscorp.wispadmin.wispadmin.service.FcmTokenService
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmMessage
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.gson.Gson
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.validation.Valid

@RestController
@RequestMapping("/fcm")
class FcmController(
    private val fcm: FirebaseMessaging,
    private val fcmTokenService: FcmTokenService
) {

    @PostMapping("save-token")
    fun registerFcmToken(@Valid @RequestBody newFcmToken: FcmTokenRequestDto): ResponseEntity<FcmTokenResponseDto> =
        ResponseEntity.ok(fcmTokenService.saveToken(newFcmToken))

    @GetMapping("get-token")
    fun getFcmToken(@RequestParam subscriptionId: Int): ResponseEntity<FcmTokenResponseDto> =
        ResponseEntity.ok(fcmTokenService.getBySubscriptionId(subscriptionId))

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
