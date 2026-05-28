package com.dscorp.wispadmin.wispadmin.controller
import com.dscorp.wispadmin.wispadmin.data.model.Face_data
import com.dscorp.wispadmin.wispadmin.repository.FaceDataRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SaveFaceDataBody
import com.dscorp.wispadmin.wispadmin.service.FacePhotoDescriptorService
import com.dscorp.wispadmin.wispadmin.service.FacePhotoQualityService
import com.dscorp.wispadmin.wispadmin.service.FaceVerifyService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import javax.annotation.PostConstruct
import java.util.Date

@RestController
@RequestMapping("/api/face-data")
@CrossOrigin(origins = ["http://localhost:5173"])
class FaceDataController(
    private val faceDataRepository: FaceDataRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
    private val jdbcTemplate: JdbcTemplate,
    private val facePhotoDescriptorService: FacePhotoDescriptorService,
    private val facePhotoQualityService: FacePhotoQualityService,
    private val faceVerifyService: FaceVerifyService
) {
    @PostConstruct
    fun ensureFaceEmbeddingColumnSize() {
        jdbcTemplate.execute("ALTER TABLE face_data MODIFY COLUMN face_embedding LONGTEXT NOT NULL")
    }

    @GetMapping
    fun getAll(): ResponseEntity<List<Map<String, Any?>>> {
        val data = faceDataRepository.findAll().map {
            mapOf(
                "id" to it.id,
                "faceEmbedding" to it.faceEmbedding,
                "imageUrl" to it.imageUrl,
                "userId" to it.user.id
            )
        }
        return ResponseEntity.ok(data)
    }

    @PostMapping
    fun save(@RequestBody body: SaveFaceDataBody): ResponseEntity<Any> {
        val userId = body.userId ?: body.user?.id
        if (userId == null) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(mapOf("message" to "Falta userId o user.id."))
        }

        val user = userRepository.findById(userId).orElse(null)
            ?: return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(mapOf("message" to "Usuario no encontrado: $userId"))

        val embedding = when {
            !body.faceEmbedding.isNullOrBlank() -> body.faceEmbedding
            !body.descriptor.isNullOrEmpty() -> objectMapper.writeValueAsString(body.descriptor)
            else -> null
        }

        if (embedding.isNullOrBlank()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(mapOf("message" to "Falta faceEmbedding o descriptor."))
        }

        val faceData = faceDataRepository.findByUser_Id(userId)?.apply {
            faceEmbedding = embedding
            imageUrl = body.imageUrl
            this.user = user
            createdAt = Date()
        } ?: Face_data(
            faceEmbedding = embedding,
            imageUrl = body.imageUrl,
            user = user
        )

        val saved = faceDataRepository.save(faceData)
        faceVerifyService.clearFaceEmbeddingCache()
        return ResponseEntity.ok(
            mapOf(
                "id" to saved.id,
                "userId" to saved.user.id,
                "message" to "Rostro registrado correctamente."
            )
        )
    }

    // Registro facial nuevo con DJL: recibe una foto, genera el embedding en backend y actualiza face_data.
    @PostMapping("/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun saveFromPhoto(
        @RequestParam("userId") userId: Int,
        @RequestParam("photo") photo: MultipartFile
    ): ResponseEntity<Any> {
        val user = userRepository.findById(userId).orElse(null)
            ?: return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(mapOf("message" to "Usuario no encontrado: $userId"))

        if (photo.isEmpty) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(mapOf("message" to "La foto facial esta vacia."))
        }

        val photoBytes = photo.bytes
        if (!facePhotoQualityService.hasUsableFaceCandidate(photoBytes)) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(mapOf("message" to "No se detecto un rostro claro."))
        }

        val descriptor = facePhotoDescriptorService.generateDescriptor(photoBytes)
            ?: return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(mapOf("message" to "No se pudo generar descriptor facial con DJL."))

        val embedding = objectMapper.writeValueAsString(descriptor)
        val faceData = faceDataRepository.findByUser_Id(userId)?.apply {
            faceEmbedding = embedding
            imageUrl = null
            this.user = user
            createdAt = Date()
        } ?: Face_data(
            faceEmbedding = embedding,
            imageUrl = null,
            user = user
        )

        val saved = faceDataRepository.save(faceData)
        faceVerifyService.clearFaceEmbeddingCache()

        return ResponseEntity.ok(
            mapOf(
                "id" to saved.id,
                "userId" to saved.user.id,
                "descriptorSize" to descriptor.size,
                "message" to "Rostro registrado correctamente con DJL."
            )
        )
    }

    // Valida una foto sin guardar datos; se usa para habilitar el boton solo cuando DJL detecta un rostro usable.
    @PostMapping("/photo/check", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun checkPhoto(@RequestParam("photo") photo: MultipartFile): ResponseEntity<Any> {
        if (photo.isEmpty) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(mapOf("valid" to false, "message" to "La foto facial esta vacia."))
        }

        val photoBytes = photo.bytes
        if (!facePhotoQualityService.hasUsableFaceCandidate(photoBytes)) {
            return ResponseEntity.ok(
                mapOf("valid" to false, "message" to "No se detecto un rostro claro.")
            )
        }

        val descriptor = facePhotoDescriptorService.generateDescriptor(photoBytes)
            ?: return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(mapOf("valid" to false, "message" to "No se detecto un rostro claro."))

        return ResponseEntity.ok(
            mapOf(
                "valid" to true,
                "descriptorSize" to descriptor.size,
                "message" to "Rostro detectado."
            )
        )
    }
}
