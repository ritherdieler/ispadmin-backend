package com.dscorp.wispadmin.wispadmin.controller
import com.dscorp.wispadmin.wispadmin.config.FaceRecognitionProperties
import com.dscorp.wispadmin.wispadmin.data.model.Face_data
import com.dscorp.wispadmin.wispadmin.data.model.Face_data.FaceAngle
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
import com.dscorp.wispadmin.wispadmin.util.FaceEmbeddingMath
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
import javax.transaction.Transactional
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
    private val faceVerifyService: FaceVerifyService,
    private val faceRecognitionProperties: FaceRecognitionProperties
) {
    @PostConstruct
    fun ensureFaceEmbeddingColumnSize() {
        jdbcTemplate.execute("ALTER TABLE face_data MODIFY COLUMN face_embedding LONGTEXT NOT NULL")
        dropLegacyUniqueUserIdIndexes()
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
                metric = faceRecognitionProperties.matching.offlineMetric,
                threshold = faceRecognitionProperties.matching.offlineThreshold,
                minMargin = faceRecognitionProperties.matching.offlineMinMargin,
                descriptorSize = faceRecognitionProperties.matching.offlineDescriptorSize,
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

        val faceData = faceDataRepository.findTopByUser_IdOrderByCreatedAtDesc(userId)?.apply {
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
        val faceData = faceDataRepository.findTopByUser_IdOrderByCreatedAtDesc(userId)?.apply {
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
        val faceData = faceDataRepository.findTopByUser_IdOrderByCreatedAtDesc(user.id)?.apply {
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

    // Registro facial multiangulo: reemplaza el registro facial del usuario por tres embeddings.
    @PostMapping("/photo/enroll/multi-angle", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Transactional
    fun enrollMultiAngleFromPhoto(
        @RequestParam("username") username: String,
        @RequestParam("password") password: String,
        @RequestParam("frontPhoto") frontPhoto: MultipartFile,
        @RequestParam("leftPhoto") leftPhoto: MultipartFile,
        @RequestParam("rightPhoto") rightPhoto: MultipartFile
    ): ResponseEntity<Any> {
        val normalizedUsername = username.trim()
        val user = userRepository.findByUsername(normalizedUsername)
            ?: userRepository.findByUsernameIgnoreCase(normalizedUsername)
            ?: return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("message" to "Credenciales incorrectas."))

        if (!PasswordHashUtil.matches(password, user.password)) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("message" to "Credenciales incorrectas."))
        }

        if (!user.verified) {
            return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(mapOf("message" to "Usuario no habilitado para registrar rostro."))
        }

        val missingAngles = listOfNotNull(
            "FRONT".takeIf { frontPhoto.isEmpty },
            "LEFT".takeIf { leftPhoto.isEmpty },
            "RIGHT".takeIf { rightPhoto.isEmpty }
        )

        if (missingAngles.isNotEmpty()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(mapOf("message" to "Faltan capturas faciales: ${missingAngles.joinToString(", ")}."))
        }

        val descriptors = listOf(
            FaceAngle.FRONT to generateEnrollmentDescriptor(FaceAngle.FRONT, frontPhoto),
            FaceAngle.LEFT to generateEnrollmentDescriptor(FaceAngle.LEFT, leftPhoto),
            FaceAngle.RIGHT to generateEnrollmentDescriptor(FaceAngle.RIGHT, rightPhoto)
        )

        descriptors.firstOrNull { it.second.descriptor == null }?.let { (_, result) ->
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(mapOf("message" to result.errorMessage))
        }

        faceDataRepository.deleteAllByUser_Id(user.id)

        val angleEmbeddings = descriptors.mapNotNull { (_, result) -> result.descriptor }
        val masterEmbedding = FaceEmbeddingMath.averageL2Normalized(angleEmbeddings)

        val savedFaces = descriptors.map { (angle, result) ->
            Face_data(
                faceEmbedding = objectMapper.writeValueAsString(result.descriptor),
                imageUrl = null,
                angle = angle,
                user = user
            )
        }.toMutableList()

        if (masterEmbedding != null) {
            savedFaces.add(
                Face_data(
                    faceEmbedding = objectMapper.writeValueAsString(masterEmbedding),
                    imageUrl = null,
                    angle = FaceAngle.MASTER,
                    user = user
                )
            )
        }

        val persistedFaces = faceDataRepository.saveAll(savedFaces)

        faceVerifyService.clearFaceEmbeddingCache()

        return ResponseEntity.ok(
            mapOf(
                "userId" to user.id,
                "registeredAngles" to persistedFaces.map { it.angle.name },
                "message" to "Rostro registrado correctamente en tres angulos con template maestro."
            )
        )
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

    // Inventario de embeddings agrupado por dimension del vector.
    // Util durante una migracion de modelo para ver cuantos usuarios siguen con el modelo anterior.
    @GetMapping("/admin/embedding-inventory")
    fun embeddingInventory(): ResponseEntity<Any> {
        val all = faceDataRepository.findAll()
        val bySize = all.groupingBy { parseEmbedding(it.faceEmbedding)?.size ?: -1 }.eachCount()
        val usersBySize = all
            .mapNotNull { face -> parseEmbedding(face.faceEmbedding)?.size?.let { it to face.user.id } }
            .groupBy({ it.first }, { it.second })
            .mapValues { entry -> entry.value.distinct().size }

        return ResponseEntity.ok(
            mapOf(
                "totalEmbeddings" to all.size,
                "embeddingsByDescriptorSize" to bySize,
                "usersByDescriptorSize" to usersBySize
            )
        )
    }

    // Invalida todos los registros faciales para forzar el re-enrolamiento de todos los usuarios.
    // Necesario al cambiar el modelo de embeddings (el espacio vectorial cambia y los vectores viejos quedan inservibles).
    @PostMapping("/admin/reset-embeddings")
    @Transactional
    fun resetEmbeddings(@RequestParam("confirm") confirm: String): ResponseEntity<Any> {
        if (confirm != "DELETE_ALL_FACE_DATA") {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(mapOf("message" to "Confirmacion invalida. Envia confirm=DELETE_ALL_FACE_DATA para borrar todos los rostros."))
        }

        val deleted = faceDataRepository.count()
        faceDataRepository.deleteAll()
        faceVerifyService.clearFaceEmbeddingCache()

        return ResponseEntity.ok(
            mapOf(
                "deleted" to deleted,
                "message" to "Se invalidaron todos los registros faciales. Todos los usuarios deben re-enrolar su rostro."
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

    private fun generateEnrollmentDescriptor(angle: FaceAngle, photo: MultipartFile): EnrollmentDescriptorResult {
        val angleLabel = when (angle) {
            FaceAngle.FRONT -> "frontal"
            FaceAngle.LEFT -> "izquierda"
            FaceAngle.RIGHT -> "derecha"
            FaceAngle.MASTER -> "maestro"
        }

        val photoBytes = photo.bytes
        if (!facePhotoQualityService.hasUsableFaceCandidate(photoBytes)) {
            return EnrollmentDescriptorResult(
                descriptor = null,
                errorMessage = "No se detecto un rostro claro en la captura $angleLabel."
            )
        }

        val descriptor = facePhotoDescriptorService.generateDescriptor(photoBytes)
            ?: return EnrollmentDescriptorResult(
                descriptor = null,
                errorMessage = "No se pudo generar descriptor facial para la captura $angleLabel."
            )

        return EnrollmentDescriptorResult(descriptor = descriptor, errorMessage = null)
    }

    private data class EnrollmentDescriptorResult(
        val descriptor: List<Double>?,
        val errorMessage: String?
    )

    private fun dropLegacyUniqueUserIdIndexes() {
        val sql = """
            SELECT s.INDEX_NAME
            FROM INFORMATION_SCHEMA.STATISTICS s
            WHERE s.TABLE_SCHEMA = DATABASE()
              AND s.TABLE_NAME = 'face_data'
              AND s.COLUMN_NAME = 'user_id'
              AND s.NON_UNIQUE = 0
              AND s.INDEX_NAME <> 'PRIMARY'
              AND NOT EXISTS (
                  SELECT 1
                  FROM INFORMATION_SCHEMA.STATISTICS s2
                  WHERE s2.TABLE_SCHEMA = s.TABLE_SCHEMA
                    AND s2.TABLE_NAME = s.TABLE_NAME
                    AND s2.INDEX_NAME = s.INDEX_NAME
                    AND s2.COLUMN_NAME <> 'user_id'
              )
        """.trimIndent()

        val indexNames = jdbcTemplate.queryForList(sql, String::class.java)
        indexNames.forEach { indexName ->
            jdbcTemplate.execute("ALTER TABLE face_data DROP INDEX `$indexName`")
        }
    }

}
