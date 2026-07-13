package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.requestbody.IdentifyFaceBody
import com.dscorp.wispadmin.wispadmin.requestbody.OfflineAttendanceSyncBody
import com.dscorp.wispadmin.wispadmin.requestbody.PasswordAttendanceBody
import com.dscorp.wispadmin.wispadmin.requestbody.VerifyFaceBody
import com.dscorp.wispadmin.wispadmin.response.ChallengeStartResponse
import com.dscorp.wispadmin.wispadmin.response.VerifyFaceResponse
import com.dscorp.wispadmin.wispadmin.service.FaceChallengeService
import com.dscorp.wispadmin.wispadmin.service.FaceVerifyService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/face")
@CrossOrigin(origins = ["http://localhost:5173"])
class FaceVerifyController(
    private val faceVerifyService: FaceVerifyService,
    private val faceChallengeService: FaceChallengeService
) {
    // Nivel B: el frontend pide un reto de un solo uso al abrir la sesion de marcacion.
    @PostMapping("/challenge/start")
    fun startChallenge(): ResponseEntity<ChallengeStartResponse> {
        return ResponseEntity.ok(faceChallengeService.start())
    }

    @PostMapping("/identify")
    fun identify(@RequestBody body: IdentifyFaceBody): ResponseEntity<VerifyFaceResponse> {
        return ResponseEntity.ok(faceVerifyService.identify(body))
    }

    // Identifica rostro desde foto; el backend genera el descriptor con DJL.
    @PostMapping("/identify/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun identifyPhoto(
        @RequestParam("photo") photo: MultipartFile,
        @RequestParam("challengeToken", required = false) challengeToken: String?
    ): ResponseEntity<VerifyFaceResponse> {
        return ResponseEntity.ok(faceVerifyService.identifyFromPhoto(photo, challengeToken))
    }

    @PostMapping("/verify")
    fun verify(@RequestBody body: VerifyFaceBody): ResponseEntity<VerifyFaceResponse> {
        return ResponseEntity.ok(faceVerifyService.verifyAndMark(body))
    }

    // Marca asistencia/salida desde foto; evita comparar face-api.js contra embeddings DJL.
    @PostMapping("/verify/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun verifyPhoto(
        @RequestParam("photo") photo: MultipartFile,
        @RequestParam("action") action: VerifyFaceBody.Action,
        @RequestParam("occurredAtMillis", required = false) occurredAtMillis: Long?,
        @RequestParam("challengeToken", required = false) challengeToken: String?,
        @RequestParam("attendanceStatus", required = false) attendanceStatus: String?
    ): ResponseEntity<VerifyFaceResponse> {
        return ResponseEntity.ok(faceVerifyService.verifyAndMarkFromPhoto(photo, action, occurredAtMillis, challengeToken, attendanceStatus))
    }

    // Fallback de asistencia: valida credenciales y registra entrada/salida sin depender de la camara.
    @PostMapping("/verify/password")
    fun verifyPassword(@RequestBody body: PasswordAttendanceBody): ResponseEntity<VerifyFaceResponse> {
        return ResponseEntity.ok(faceVerifyService.verifyAndMarkWithPassword(body))
    }

    // Recibe marcaciones realizadas sin conexion y las registra con la hora original del dispositivo.
    @PostMapping("/attendance/offline-sync")
    fun syncOfflineAttendance(@RequestBody body: OfflineAttendanceSyncBody): ResponseEntity<VerifyFaceResponse> {
        return ResponseEntity.ok(faceVerifyService.verifyAndMarkOffline(body))
    }
}