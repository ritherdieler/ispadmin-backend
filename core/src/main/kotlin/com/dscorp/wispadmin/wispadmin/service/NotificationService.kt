package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.controller.ModuleException
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmMessage
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmMessage.FcmMessageType
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class NotificationService(
    private val fcm: FirebaseMessaging,
    private val userRepository: UserRepository
) {
    
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)
    
    // Crear una instancia de Gson con un adaptador personalizado para LocalDate
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
        .create()
    
    /**
     * Send a notification to a topic
     */
    fun sendTopicNotification(
        topic: String,
        title: String,
        message: String,
        data: Any? = null,
        type: FcmMessageType,
        id: String
    ) {
        try {
            val notification = FcmMessage(
                title = title,
                message = message,
                topic = topic,
                data = data?.let { gson.toJson(it) },
                type = type,
                id = id
            )
            
            val msg = Message.builder()
                .setTopic(topic)
                .putData("title", title)
                .putData("message", message)
                .putData("body", gson.toJson(notification))
                .build()
                
            val response = fcm.send(msg)
            logger.info("Successfully sent notification to topic $topic: $response")
        } catch (e: Exception) {
            logger.error("Error sending notification to topic $topic", e)
        }
    }
    
    /**
     * Send a notification to a specific device token
     */
    fun sendDeviceNotification(
        token: String,
        title: String,
        message: String,
        data: Any? = null,
        type:  FcmMessageType,
        id: String
    ) {
        try {
            val notification = FcmMessage(
                title = title,
                message = message,
                customerToken = token,
                data = data?.let { gson.toJson(it) },
                type = type,
                id = id
            )
            
            val msg = Message.builder()
                .setToken(token)
                .putData("title", title)
                .putData("message", message)
                .putData("body", gson.toJson(notification))
                .build()
                
            val response = fcm.send(msg)
            logger.info("Successfully sent notification to device $token: $response")
        } catch (e: Exception) {
            logger.error("Error sending notification to device $token", e)
        }
    }
    
    /**
     * Send a notification to a specific user by their ID
     */
    fun sendUserNotification(
        userId: Int,
        title: String,
        message: String,
        data: Any? = null,
        type: FcmMessageType,
        id: String
    ) {
        try {
            // Buscar el usuario por su ID
            val user = userRepository.findById(userId)
                .orElseThrow { ModuleException("Usuario no encontrado con ID: $userId", "NOTIFICATION") }
            
            // Verificar si el usuario tiene un token de dispositivo
            val deviceToken = user.deviceToken
            if (deviceToken.isNullOrEmpty()) {
                logger.warn("No hay token de dispositivo para el usuario con ID: $userId")
                return
            }
            
            // Enviar la notificación utilizando el método existente
            sendDeviceNotification(
                token = deviceToken,
                title = title,
                message = message,
                data = data,
                type = type,
                id = id
            )
            
            logger.info("Notificación enviada al usuario con ID: $userId")
        } catch (e: Exception) {
            logger.error("Error al enviar notificación al usuario con ID: $userId", e)
        }
    }
}

/**
 * TypeAdapter personalizado para serializar/deserializar LocalDate
 */
class LocalDateTimeAdapter : TypeAdapter<LocalDateTime>() {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    
    override fun write(out: JsonWriter, value: LocalDateTime?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.value(formatter.format(value))
    }
    
    override fun read(reader: JsonReader): LocalDateTime? {
        if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        val dateTimeStr = reader.nextString()
        return LocalDateTime.parse(dateTimeStr, formatter)
    }
}
