package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.data.model.User
import com.dscorp.wispadmin.wispadmin.dto.UserDto
import com.dscorp.wispadmin.wispadmin.mapper.toDto
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.requestbody.DeviceTokenRequest
import com.dscorp.wispadmin.wispadmin.requestbody.LoginBody
import com.dscorp.wispadmin.wispadmin.service.UserService
import com.dscorp.wispadmin.wispadmin.extensions.toErrorLog
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmConstants
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmMessage
import com.dscorp.wispadmin.wispadmin.util.PasswordHashUtil
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import com.dscorp.wispadmin.wispadmin.requestbody.FaceLoginBody// agregue 2 imports
import com.dscorp.wispadmin.wispadmin.service.FaceVerifyService

@RestController
@RequestMapping("users")
class UserController @Autowired constructor(
    private val userService: UserService,
    private val repository: UserRepository,
    private val errorLogRepository: ErrorLogRepository,
    private val fcm: FirebaseMessaging,
    private val faceVerifyService: FaceVerifyService // agregue edwin
) {

    val serverErrorResponse: ResponseEntity<UserDto> = ResponseEntity.status(500).body(null)
    val listErrorResponse: ResponseEntity<List<UserDto>> = ResponseEntity.status(500).body(null)

    @GetMapping
    fun getAllUsers(): ResponseEntity<List<UserDto>> {
        return try {
            val users = repository.findAll()
            ResponseEntity.ok(users.map { it.toDto() })
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.USER))
            listErrorResponse
        }
    }

    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Int): ResponseEntity<UserDto> {
        return try {
            val user = repository.findById(id)
            if (user.isPresent) {
                ResponseEntity.ok(user.get().toDto())
            } else {
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.USER))
            serverErrorResponse
        }
    }

    @PostMapping
    fun newUser(@RequestBody newUser: User): ResponseEntity<UserDto>? {
        return try {
            if (!newUser.password.isNullOrBlank()) {
                newUser.password = PasswordHashUtil.hash(newUser.password!!)
            }
            val user = repository.save(newUser)
            ResponseEntity.status(200).body(user.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.USER))

            return when (e) {
                is DataIntegrityViolationException -> ResponseEntity.status(409).body(null)
                else -> ResponseEntity.status(500).body(null)
            }
        }
    }

    @PutMapping("/{id}")
    fun updateUser(@PathVariable id: Int, @RequestBody updatedUser: User): ResponseEntity<UserDto> {
        return try {
            val existingUser = repository.findById(id)
            if (existingUser.isPresent) {
                val user = existingUser.get()
                val wasVerified = user.verified
                
                user.name = updatedUser.name
                user.lastName = updatedUser.lastName
                user.email = updatedUser.email
                user.phone = updatedUser.phone
                user.dni = updatedUser.dni
                user.type = updatedUser.type
                user.verified = updatedUser.verified
                
                // Solo actualizar password si se proporciona uno nuevo
                if (!updatedUser.password.isNullOrBlank()) {
                    user.password = PasswordHashUtil.hash(updatedUser.password!!)
                }
                
                val savedUser = repository.save(user)
                
                // Verificar si el usuario cambió de verificado a no verificado
                if (wasVerified && !savedUser.verified) {
                    sendForceLogoutNotification(savedUser)
                }
                
                ResponseEntity.ok(savedUser.toDto())
            } else {
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.USER))
            serverErrorResponse
        }
    }

    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: Int): ResponseEntity<Void> {
        return try {
            val user = repository.findById(id)
            if (user.isPresent) {
                repository.deleteById(id)
                ResponseEntity.ok().build()
            } else {
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.USER))
            ResponseEntity.status(500).build()
        }
    }

    @PostMapping("/login")
    fun login(@RequestBody user: LoginBody): ResponseEntity<UserDto> {
        return try {
            val username = user.username.trim()
            val foundUser = repository.findByUsername(username) ?: repository.findByUsernameIgnoreCase(username)
            if (foundUser != null && PasswordHashUtil.matches(user.password, foundUser.password)) {
                if (!PasswordHashUtil.isHashed(foundUser.password)) {
                    foundUser.password = PasswordHashUtil.passwordToStoreAfterLegacyLogin(user.password, foundUser.password)
                    repository.save(foundUser)
                }
                return ResponseEntity.ok(foundUser.toDto())
            }
            return ResponseEntity.notFound().build()

        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.USER))

            serverErrorResponse
        }
    }
    // Agregue Endpoint  para App Android 15/05/2026
    @PostMapping("/login/face")
    fun loginWithFace(@RequestBody body: FaceLoginBody): ResponseEntity<UserDto> {
        return try {
            val user = faceVerifyService.indentifyUserForLogin(body.descriptor)
                ?: return ResponseEntity.status(401).body(null)

            ResponseEntity.ok(user.toDto())
        } catch (e: Exception){
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.USER))
            serverErrorResponse
        }
    }

    // Login facial nuevo para Android: recibe foto nativa y el backend genera el descriptor.
    @PostMapping("/login/face/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun loginWithFacePhoto(@RequestPart("photo") photo: MultipartFile): ResponseEntity<UserDto> {
        return try {
            val user = faceVerifyService.identifyUserFromPhotoForLogin(photo)
                ?: return ResponseEntity.status(401).body(null)

            ResponseEntity.ok(user.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.USER))
            serverErrorResponse
        }
    }

    @PutMapping("/device-token")
    fun updateDeviceToken(
        @RequestBody request: DeviceTokenRequest
    ): ResponseEntity<UserDto> {
        return try {
            val updatedUser = userService.updateDeviceToken(request.userId, request.deviceToken)
            ResponseEntity.ok(updatedUser.toDto())
        } catch (e: Exception) {
            ResponseEntity.status(500).body(null)
        }
    }

    /**
     * Envía una notificación de force logout al usuario específico
     * cuando su estado de verificación cambia de verificado a no verificado
     */
    private fun sendForceLogoutNotification(user: User) {
        try {
            // Si el usuario tiene un device token, enviar notificación específica
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
            
            // También enviar notificación general para todos los dispositivos
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
            // Log del error pero no fallar la actualización del usuario
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.USER))
        }
    }

}
