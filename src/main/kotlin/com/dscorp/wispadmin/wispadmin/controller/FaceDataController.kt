package com.dscorp.wispadmin.wispadmin.controller
import com.dscorp.wispadmin.wispadmin.data.model.Face_data
import com.dscorp.wispadmin.wispadmin.dto.UserDto
import com.dscorp.wispadmin.wispadmin.mapper.toDto
import com.dscorp.wispadmin.wispadmin.repository.FaceDataRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SaveFaceDataBody
import com.dscorp.wispadmin.wispadmin.response.OfflineFaceDatasetResponse
import com.dscorp.wispadmin.wispadmin.response.OfflineFaceItemResponse
import com.dscorp.wispadmin.wispadmin.service.FacePhotoDescriptorService
import com.dscorp.wispadmin.wispadmin.service.FacePhotoQualityService
import com.dscorp.wispadmin.wispadmin.service.FaceVerifyService
import com.dscorp.wispadmin.wispadmin.util.PasswordHashUtil
import com.fasterxml.jackson.core.type.TypeReference
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
import org.springframework.web.bind.annotation.PathVariable
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
    companion object {
        private const val OFFLINE_FACE_METRIC = "COSINE_SIMILARITY"
        private const val OFFLINE_FACE_THRESHOLD = 0.80
        private const val OFFLINE_FACE_MIN_MARGIN = 0.04
        private const val OFFLINE_DESCRIPTOR_SIZE = 512
    }

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

    // Entrega el dataset facial minimo para que el frontend pueda preparar el modo offline.
    // No modifica registros existentes y mantiene separado el endpoint legacy /api/face-data.
    @GetMapping("/offline-dataset")
    fun getOfflineDataset(): ResponseEntity<OfflineFaceDatasetResponse> {
        val faces = faceDataRepository.findAll().mapNotNull { faceData ->
            val embedding = parseEmbedding(faceData.faceEmbedding) ?: return@mapNotNull null
            val user = faceData.user

            OfflineFaceItemResponse(
                faceDataId = faceData.id,
                userId = user.id,
                userName = "${user.name ?: ""} ${user.lastName ?: ""}".trim().ifBlank {
                    user.username ?: "Usuario"
                },
                userDni = user.dni,
                userType = user.type?.name,
                faceEmbedding = embedding,
                registeredAt = faceData.createdAt
            )
        }

        return ResponseEntity.ok(
            OfflineFaceDatasetResponse(
                datasetVersion = faces.maxOfOrNull { it.registeredAt.time } ?: System.currentTimeMillis(),
                generatedAt = Date(),
                metric = OFFLINE_FACE_METRIC,
                threshold = OFFLINE_FACE_THRESHOLD,
                minMargin = OFFLINE_FACE_MIN_MARGIN,
                descriptorSize = OFFLINE_DESCRIPTOR_SIZE,
                faces = faces
            )
        )
    }

    @GetMapping("/user/{userId}/exists")
    fun existsByUserId(@PathVariable userId: Int): ResponseEntity<Any> {
        if (!userRepository.existsById(userId)) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(mapOf("hasFace" to false, "message" to "Usuario no encontrado."))
        }
        return ResponseEntity.ok(
            mapOf("hasFace" to faceDataRepository.existsByUser_Id(userId))
        )
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

    // Registra el rostro desde la app despues de validar la identidad del usuario.
    // Este endpoint evita confiar solamente en un userId enviado por el dispositivo.
    @PostMapping("/photo/enroll", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun enrollFromPhoto(
        @RequestParam("username") username: String,
        @RequestParam("password") password: String,
        @RequestParam("photo") photo: MultipartFile
    ): ResponseEntity<UserDto> {
        val normalizedUsername = username.trim()
        val user = userRepository.findByUsername(normalizedUsername)
            ?: userRepository.findByUsernameIgnoreCase(normalizedUsername)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null)

        if (!PasswordHashUtil.matches(password, user.password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null)
        }

        if (!user.verified) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null)
        }

        if (photo.isEmpty) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null)
        }

        val photoBytes = photo.bytes
        if (!facePhotoQualityService.hasUsableFaceCandidate(photoBytes)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null)
        }

        val descriptor = facePhotoDescriptorService.generateDescriptor(photoBytes)
            ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null)

        val embedding = objectMapper.writeValueAsString(descriptor)
        val faceData = faceDataRepository.findByUser_Id(user.id)?.apply {
            faceEmbedding = embedding
            imageUrl = null
            this.user = user
            createdAt = Date()
        } ?: Face_data(
            faceEmbedding = embedding,
            imageUrl = null,
            user = user
        )

        faceDataRepository.save(faceData)
        faceVerifyService.clearFaceEmbeddingCache()

        return ResponseEntity.ok(user.toDto())
    }

    // Valida una foto sin guardar datos; se usa para habilitar el boton solo cuando DJL detecta un rostro usable.
    @PostMapping("/photo/check", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun checkPhoto(@RequestParam("photo") photo: MultipartFile): ResponseEntity<Any> {
        if (photo.isEmpty) {
            return ResponseEntity.ok(
                mapOf("valid" to false, "message" to "La foto facial esta vacia.")
            )
        }

        val photoBytes = photo.bytes
        if (!facePhotoQualityService.hasUsableFaceCandidate(photoBytes)) {
            return ResponseEntity.ok(
                mapOf("valid" to false, "message" to "No se detecto un rostro claro.")
            )
        }

        val descriptors = facePhotoDescriptorService.generateDescriptorCandidates(photoBytes)
        val descriptor = descriptors.firstOrNull()
            ?: return ResponseEntity.ok(
                mapOf("valid" to false, "message" to "No se pudo generar descriptor facial.")
            )

        return ResponseEntity.ok(
            mapOf(
                "valid" to true,
                "descriptorSize" to descriptor.size,
                "message" to "Rostro detectado."
            )
        )
    }

    private fun parseEmbedding(json: String): List<Double>? {
        return try {
            objectMapper.readValue(json, object : TypeReference<List<Double>>() {})
        } catch (e: Exception) {
            null
        }
    }
}
