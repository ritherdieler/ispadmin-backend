package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.requestbody.FaceEvidenceBody
import com.dscorp.wispadmin.wispadmin.service.FirebaseStorageService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

@RestController
@RequestMapping("/api/face/evidence")
class FaceEvidenceController(
    private val storageService: FirebaseStorageService
) {

    @PostMapping
    fun saveEvidence(@RequestBody body: FaceEvidenceBody): ResponseEntity<Map<String, String>> {
        // Recibe la foto enviada por la camara y la guarda unicamente en Firebase Storage.
        if (body.imageBase64.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("message" to "Imagen requerida"))
        }

        val commaIndex = body.imageBase64.indexOf(',')
        val payload = if (commaIndex >= 0) body.imageBase64.substring(commaIndex + 1) else body.imageBase64
        val bytes = try {
            Base64.getDecoder().decode(payload)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("message" to "Imagen invalida"))
        }

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
        val safeReason = body.reason.ifBlank { "SUSPICIOUS_FACE" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val userPart = body.userId?.let { "user_$it" } ?: "unknown_user"
        val filename = "${timestamp}_${userPart}_${safeReason}.jpg"
        val firebaseUrl = storageService.uploadBytesToFolder(bytes, filename, "attendance")

        return ResponseEntity.ok(
            mapOf(
                "message" to "Evidencia guardada en Firebase",
                "filename" to filename,
                "url" to firebaseUrl
            )
        )
    }
}
