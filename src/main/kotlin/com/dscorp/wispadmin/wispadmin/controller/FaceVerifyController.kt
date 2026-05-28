package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.requestbody.IdentifyFaceBody
import com.dscorp.wispadmin.wispadmin.requestbody.VerifyFaceBody
import com.dscorp.wispadmin.wispadmin.response.VerifyFaceResponse
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
    private val faceVerifyService: FaceVerifyService
) {
    @PostMapping("/identify")
    fun identify(@RequestBody body: IdentifyFaceBody): ResponseEntity<VerifyFaceResponse> {
        return ResponseEntity.ok(faceVerifyService.identify(body))
    }

    // Identifica rostro desde foto; el backend genera el descriptor con DJL.
    @PostMapping("/identify/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun identifyPhoto(@RequestParam("photo") photo: MultipartFile): ResponseEntity<VerifyFaceResponse> {
        return ResponseEntity.ok(faceVerifyService.identifyFromPhoto(photo))
    }

    @PostMapping("/verify")
    fun verify(@RequestBody body: VerifyFaceBody): ResponseEntity<VerifyFaceResponse> {
        return ResponseEntity.ok(faceVerifyService.verifyAndMark(body))
    }

    // Marca asistencia/salida desde foto; evita comparar face-api.js contra embeddings DJL.
    @PostMapping("/verify/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun verifyPhoto(
        @RequestParam("photo") photo: MultipartFile,
        @RequestParam("action") action: VerifyFaceBody.Action
    ): ResponseEntity<VerifyFaceResponse> {
        return ResponseEntity.ok(faceVerifyService.verifyAndMarkFromPhoto(photo, action))
    }
}
