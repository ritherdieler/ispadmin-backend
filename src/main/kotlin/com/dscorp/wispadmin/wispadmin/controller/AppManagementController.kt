package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.requestbody.AppManagementRequest
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmConstants
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmMessage
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

enum class ManagementAction {
    FORCE_LOGOUT,
    UPDATE_APP
}


@RestController
@RequestMapping("/management")
class AppManagementController(
    private val fcm: FirebaseMessaging,
) {
    @PostMapping("app_force_logout")
    fun forgeLogOut(@RequestBody request: AppManagementRequest): ResponseEntity<Void> {
        return when (request.action) {
            ManagementAction.FORCE_LOGOUT -> {
                val data = mapOf(FcmConstants.MANAGEMENT_ACTION to FcmConstants.FORCE_LOGOUT)

                val notification = FcmMessage(
                    title = "Invalidate_session",
                    type = FcmMessage.FcmMessageType.APP_MANAGEMENT,
                    message = "Logout forced by admin",
                    topic = FcmConstants.FCM_ALL_TOPIC,
                    data = data
                )
                notification.sendNotification(fcm)
                ResponseEntity.ok().build()
            }

            ManagementAction.UPDATE_APP -> {
                println("Update app")
                ResponseEntity.ok().build()
            }

        }
    }

}
