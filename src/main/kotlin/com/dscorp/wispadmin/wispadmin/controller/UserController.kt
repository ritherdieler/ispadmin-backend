package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.observability.security.ObservabilitySessionTokenService
import com.dscorp.wispadmin.wispadmin.data.model.User
import com.dscorp.wispadmin.wispadmin.dto.UserDto
import com.dscorp.wispadmin.wispadmin.mapper.toDto
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.requestbody.DeviceTokenRequest
import com.dscorp.wispadmin.wispadmin.requestbody.LoginBody
import com.dscorp.wispadmin.wispadmin.service.UserService
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmConstants
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmMessage
import com.dscorp.wispadmin.wispadmin.util.PasswordHashUtil
import com.google.firebase.messaging.FirebaseMessaging
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import com.dscorp.wispadmin.wispadmin.requestbody.FaceLoginBody
import com.dscorp.wispadmin.wispadmin.service.FaceVerifyService

@RestController
@RequestMapping("users")
class UserController(
    private val userService: UserService,
    private val repository: UserRepository,
    private val fcm: FirebaseMessaging,
    private val faceVerifyService: FaceVerifyService,
    private val observabilitySessionTokenService: ObservabilitySessionTokenService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(UserController::class.java)
    }

    @GetMapping
    fun getAllUsers(): ResponseEntity<List<UserDto>> =
        ResponseEntity.ok(repository.findAll().map { it.toDto() })

    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Int): ResponseEntity<UserDto> {
        val user = repository.findById(id)
        return if (user.isPresent) {
            ResponseEntity.ok(user.get().toDto())
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping
    fun newUser(@RequestBody newUser: User): ResponseEntity<UserDto> {
        if (!newUser.password.isNullOrBlank()) {
            newUser.password = PasswordHashUtil.hash(newUser.password!!)
        }
        return try {
            ResponseEntity.ok(repository.save(newUser).toDto())
        } catch (e: DataIntegrityViolationException) {
            ResponseEntity.status(409).body(null)
        }
    }

    @PutMapping("/{id}")
    fun updateUser(@PathVariable id: Int, @RequestBody updatedUser: User): ResponseEntity<UserDto> {
        val existingUser = repository.findById(id)
        if (!existingUser.isPresent) {
            return ResponseEntity.notFound().build()
        }

        val user = existingUser.get()
        val wasVerified = user.verified

        user.name = updatedUser.name
        user.lastName = updatedUser.lastName
        user.email = updatedUser.email
        user.phone = updatedUser.phone
        user.dni = updatedUser.dni
        user.type = updatedUser.type
        user.verified = updatedUser.verified

        if (!updatedUser.password.isNullOrBlank()) {
            user.password = PasswordHashUtil.hash(updatedUser.password!!)
        }

        val savedUser = repository.save(user)

        if (wasVerified && !savedUser.verified) {
            sendForceLogoutNotification(savedUser)
        }

        return ResponseEntity.ok(savedUser.toDto())
    }

    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: Int): ResponseEntity<Void> {
        val user = repository.findById(id)
        return if (user.isPresent) {
            repository.deleteById(id)
            ResponseEntity.ok().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/login")
    fun login(@RequestBody user: LoginBody): ResponseEntity<UserDto> {
        val username = user.username.trim()
        val foundUser = repository.findByUsername(username) ?: repository.findByUsernameIgnoreCase(username)
        if (foundUser != null && PasswordHashUtil.matches(user.password, foundUser.password)) {
            if (!PasswordHashUtil.isHashed(foundUser.password)) {
                foundUser.password = PasswordHashUtil.passwordToStoreAfterLegacyLogin(user.password, foundUser.password)
                repository.save(foundUser)
            }
            return ResponseEntity.ok(foundUser.toDto().withSessionTokens(foundUser))
        }
        return ResponseEntity.notFound().build()
    }

    @PostMapping("/login/face")
    fun loginWithFace(@RequestBody body: FaceLoginBody): ResponseEntity<UserDto> {
        val user = faceVerifyService.indentifyUserForLogin(body.descriptor)
            ?: return ResponseEntity.status(401).body(null)
        return ResponseEntity.ok(user.toDto().withSessionTokens(user))
    }

    @PostMapping("/login/face/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun loginWithFacePhoto(@RequestPart("photo") photo: MultipartFile): ResponseEntity<UserDto> {
        val user = faceVerifyService.identifyUserFromPhotoForLogin(photo)
            ?: return ResponseEntity.status(401).body(null)
        return ResponseEntity.ok(user.toDto().withSessionTokens(user))
    }

    @PostMapping("/token/refresh")
    fun refreshToken(@RequestBody body: RefreshTokenBody): ResponseEntity<TokenRefreshResponse> {
        val claims = observabilitySessionTokenService.verifyRefresh(body.refreshToken)
            ?: return ResponseEntity.status(401).build()
        val userId = claims.userId ?: return ResponseEntity.status(401).build()
        val user = repository.findById(userId).orElse(null)
            ?: return ResponseEntity.status(401).build()
        val access = observabilitySessionTokenService.issueAccess(user.id, user.username, user.type?.name)
        val refresh = observabilitySessionTokenService.issueRefresh(user.id, user.username, user.type?.name)
        if (access == null || refresh == null) return ResponseEntity.status(500).build()
        return ResponseEntity.ok(TokenRefreshResponse(accessToken = access, refreshToken = refresh))
    }

    private fun UserDto.withSessionTokens(user: User): UserDto {
        val access = observabilitySessionTokenService.issueAccess(user.id, user.username, user.type?.name)
        val refresh = observabilitySessionTokenService.issueRefresh(user.id, user.username, user.type?.name)
        return this.copy(
            accessToken = access,
            refreshToken = refresh,
            obsSessionToken = if (user.type == User.UserType.ADMIN) access else this.obsSessionToken
        )
    }

    @PutMapping("/device-token")
    fun updateDeviceToken(
        @RequestBody request: DeviceTokenRequest
    ): ResponseEntity<UserDto> {
        val updatedUser = userService.updateDeviceToken(request.userId, request.deviceToken)
        return ResponseEntity.ok(updatedUser.toDto())
    }

    private fun sendForceLogoutNotification(user: User) {
        try {
            user.deviceToken?.let { token ->
                val data = mapOf(
                    FcmConstants.MANAGEMENT_ACTION to FcmConstants.FORCE_LOGOUT_SINGLE_USER,
                    FcmConstants.USER_TOKEN to token
                )

                val notification = FcmMessage(
                    title = "Invalidate_session",
                    type = FcmMessage.FcmMessageType.APP_MANAGEMENT,
                    message = "Logout forced by admin - Account verification revoked",
                    customerToken = token,
                    data = data
                )
                notification.sendNotification(fcm)
            }

            val generalData = mapOf(
                FcmConstants.MANAGEMENT_ACTION to FcmConstants.FORCE_LOGOUT,
                FcmConstants.USER_TOKEN to ""
            )

            val generalNotification = FcmMessage(
                title = "Invalidate_session",
                type = FcmMessage.FcmMessageType.APP_MANAGEMENT,
                message = "Logout forced by admin - Account verification revoked",
                topic = FcmConstants.FCM_ALL_TOPIC,
                data = generalData
            )
            generalNotification.sendNotification(fcm)
        } catch (e: Exception) {
            logger.warn("No se pudo enviar la notificación de force logout al usuario {}", user.id, e)
        }
    }
}

data class RefreshTokenBody(
    val refreshToken: String = ""
)

data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String
)
