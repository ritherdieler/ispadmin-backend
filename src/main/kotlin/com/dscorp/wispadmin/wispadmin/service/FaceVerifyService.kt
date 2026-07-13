package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.FaceEmbeddingProperties
import com.dscorp.wispadmin.wispadmin.config.FaceRecognitionProperties
import com.dscorp.wispadmin.wispadmin.data.model.Attendance
import com.dscorp.wispadmin.wispadmin.data.model.Face_data
import com.dscorp.wispadmin.wispadmin.data.model.Face_data.FaceAngle
import com.dscorp.wispadmin.wispadmin.data.model.User
import com.dscorp.wispadmin.wispadmin.repository.AttendanceRepository
import com.dscorp.wispadmin.wispadmin.repository.FaceDataRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.requestbody.IdentifyFaceBody
import com.dscorp.wispadmin.wispadmin.requestbody.OfflineAttendanceSyncBody
import com.dscorp.wispadmin.wispadmin.requestbody.PasswordAttendanceBody
import com.dscorp.wispadmin.wispadmin.requestbody.VerifyFaceBody
import com.dscorp.wispadmin.wispadmin.response.VerifyFaceResponse
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import com.dscorp.wispadmin.wispadmin.util.FaceEmbeddingMath
import com.dscorp.wispadmin.wispadmin.util.PasswordHashUtil
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

@Service
@Transactional
class FaceVerifyService(
    private val faceDataRepository: FaceDataRepository,
    private val attendanceRepository: AttendanceRepository,
    private val userRepository: UserRepository,
    private val facePhotoDescriptorService: FacePhotoDescriptorService,
    private val facePhotoQualityService: FacePhotoQualityService,
    private val objectMapper: ObjectMapper,
    private val faceRecognitionProperties: FaceRecognitionProperties,
    private val faceEmbeddingProperties: FaceEmbeddingProperties,
    private val faceRecognitionMetricsLogger: FaceRecognitionMetricsLogger,
    private val faceChallengeService: FaceChallengeService
) {
    private val logger = LoggerFactory.getLogger(FaceVerifyService::class.java)
    private val faceCacheLock = Any()

    @Volatile
    private var faceEmbeddingCache: List<StoredFaceEmbedding> = emptyList()

    @Volatile
    private var faceEmbeddingCacheLoadedAt: Long = 0

    companion object {
        private const val FACE_REVALIDATION_MONTHS = 6
        private const val CHECK_IN_LIMIT_HOUR = 8
        private const val CHECK_IN_LIMIT_MINUTE = 30
        private const val CHECK_IN_TOLERANCE_MINUTES = 20
        private const val CHECK_OUT_LIMIT_HOUR = 18
        private const val CHECK_OUT_LIMIT_MINUTE = 0
    }

    fun identify(body: IdentifyFaceBody): VerifyFaceResponse {
        if (body.descriptor.isEmpty()) {
            return VerifyFaceResponse(
                matched = false,
                message = "Descriptor facial vacio."
            )
        }

        val match = findBestMatch(
            descriptor = body.descriptor,
            threshold = faceRecognitionProperties.matching.euclideanThreshold,
        )
            ?: return VerifyFaceResponse(
                matched = false,
                message = "Rostro no reconocido."
            )

        if (isFaceExpired(match.faceCreatedAt)) {
            return expiredFaceResponse(match)
        }

        return VerifyFaceResponse(
            matched = true,
            userId = match.user.id,
            userName = match.userName,
            userDni = match.user.dni,
            userType = match.user.type?.name,
            nextAction = nextAttendanceAction(match.user.id),
            message = "Rostro identificado."
        )
    }

    // Identifica un rostro desde foto usando DJL, sin depender del descriptor enviado por el navegador.
    fun identifyFromPhoto(photo: MultipartFile, challengeToken: String? = null): VerifyFaceResponse {
        if (photo.isEmpty) {
            return VerifyFaceResponse(
                matched = false,
                message = "Foto facial vacia."
            )
        }

        // La identificacion no crea registros, por eso solo valida el reto sin consumirlo (gating opcional).
        if (faceChallengeService.requireForIdentify() && !faceChallengeService.isValid(challengeToken)) {
            return challengeRequiredResponse()
        }

        val flow = "attendance-identify-photo-djl"
        val startedAt = System.currentTimeMillis()
        val photoBytes = photo.bytes
        val quality = facePhotoQualityService.evaluate(photoBytes)
        if (!quality.valid) {
            logPhotoOutcome(flow, false, "NO_CLEAR_FACE", null, null, null, false, photoBytes.size, quality, startedAt)
            return VerifyFaceResponse(
                matched = false,
                message = "No se detecto un rostro claro."
            )
        }

        val primaryDescriptor = facePhotoDescriptorService.generateDescriptor(photoBytes)
        if (primaryDescriptor == null) {
            logPhotoOutcome(flow, false, "NO_DESCRIPTOR", null, null, null, false, photoBytes.size, quality, startedAt)
            return VerifyFaceResponse(
                matched = false,
                message = "No se pudo generar descriptor facial."
            )
        }

        val photoMatch = findBestMatchWithFallbackCandidates(
            primaryDescriptor = primaryDescriptor,
            photoBytes = photoBytes,
            threshold = faceRecognitionProperties.matching.photoSimilarityThreshold,
            source = flow,
            metric = embeddingMetric(),
            probeQuality = quality.qualityScore
        )
        if (photoMatch == null) {
            logPhotoOutcome(flow, false, "NOT_RECOGNIZED", null, null, primaryDescriptor.size, false, photoBytes.size, quality, startedAt)
            return VerifyFaceResponse(
                matched = false,
                message = "Rostro no reconocido."
            )
        }

        val match = photoMatch.match
        if (isFaceExpired(match.faceCreatedAt)) {
            logPhotoOutcome(flow, false, "EXPIRED", match.user.id, match.score, primaryDescriptor.size, photoMatch.usedFallback, photoBytes.size, quality, startedAt)
            return expiredFaceResponse(match)
        }

        logPhotoOutcome(flow, true, "OK", match.user.id, match.score, primaryDescriptor.size, photoMatch.usedFallback, photoBytes.size, quality, startedAt)
        return VerifyFaceResponse(
            matched = true,
            userId = match.user.id,
            userName = match.userName,
            userDni = match.user.dni,
            userType = match.user.type?.name,
            nextAction = nextAttendanceAction(match.user.id),
            message = "Rostro identificado."
        )
    }

    fun verifyAndMark(body: VerifyFaceBody): VerifyFaceResponse {
        if (body.descriptor.isEmpty()) {
            return VerifyFaceResponse(
                matched = false,
                message = "Descriptor facial vacio."
            )
        }

        val match = findBestMatch(
            descriptor = body.descriptor,
            threshold = faceRecognitionProperties.matching.euclideanThreshold,
        )
            ?: return VerifyFaceResponse(
                matched = false,
                message = "Rostro no reconocido."
            )

        if (isFaceExpired(match.faceCreatedAt)) {
            return expiredFaceResponse(match)
        }

        return when (body.action) {
            VerifyFaceBody.Action.CHECK_IN -> registerCheckIn(match.user.id, match.userName, match.user)
            VerifyFaceBody.Action.CHECK_OUT -> registerCheckOut(match.user.id, match.userName, match.user)
        }
    }

    // Fallback operacional para asistencia: autentica usuario/contrasena y marca con metodo PASSWORD.
    fun verifyAndMarkWithPassword(body: PasswordAttendanceBody): VerifyFaceResponse {
        val username = body.username.trim()
        if (username.isBlank() || body.password.isBlank()) {
            return VerifyFaceResponse(
                matched = false,
                message = "Usuario y contrasena son obligatorios."
            )
        }

        val user = userRepository.findByUsername(username) ?: userRepository.findByUsernameIgnoreCase(username)
        if (user == null || !PasswordHashUtil.matches(body.password, user.password)) {
            return VerifyFaceResponse(
                matched = false,
                message = "Credenciales incorrectas."
            )
        }

        if (!user.verified || user.type == User.UserType.CLIENT) {
            return VerifyFaceResponse(
                matched = false,
                userId = user.id,
                userName = user.fullName(),
                userDni = user.dni,
                userType = user.type?.name,
                message = "Usuario no habilitado para marcar asistencia."
            )
        }

        if (!PasswordHashUtil.isHashed(user.password)) {
            user.password = PasswordHashUtil.passwordToStoreAfterLegacyLogin(body.password, user.password)
            userRepository.save(user)
        }

        val action = body.action ?: when (nextAttendanceAction(user.id)) {
            "CHECK_OUT" -> VerifyFaceBody.Action.CHECK_OUT
            else -> VerifyFaceBody.Action.CHECK_IN
        }

        return when (action) {
            VerifyFaceBody.Action.CHECK_IN -> registerCheckIn(user.id, user.fullName(), user, method = "PASSWORD")
            VerifyFaceBody.Action.CHECK_OUT -> registerCheckOut(user.id, user.fullName(), user, method = "PASSWORD")
        }
    }

    // Marca asistencia o salida desde foto usando el mismo motor DJL que genera face_data.
    fun verifyAndMarkFromPhoto(
        photo: MultipartFile,
        action: VerifyFaceBody.Action,
        occurredAtMillis: Long? = null,
        challengeToken: String? = null,
        attendanceStatus: String? = null
    ): VerifyFaceResponse {
        if (photo.isEmpty) {
            return VerifyFaceResponse(
                matched = false,
                message = "Foto facial vacia."
            )
        }

        // La marcacion crea asistencia: el reto de un solo uso se consume aqui para impedir replay/bypass.
        if (faceChallengeService.requireForVerify() && !faceChallengeService.consume(challengeToken)) {
            logger.info("Marcacion rechazada: reto activo invalido o ausente. action={}", action)
            return challengeRequiredResponse()
        }

        val flow = "attendance-verify-photo-djl"
        val startedAt = System.currentTimeMillis()
        val photoBytes = photo.bytes
        val quality = facePhotoQualityService.evaluate(photoBytes)
        if (!quality.valid) {
            logPhotoOutcome(flow, false, "NO_CLEAR_FACE", null, null, null, false, photoBytes.size, quality, startedAt)
            return VerifyFaceResponse(
                matched = false,
                message = "No se detecto un rostro claro."
            )
        }

        val primaryDescriptor = facePhotoDescriptorService.generateDescriptor(photoBytes)
        if (primaryDescriptor == null) {
            logPhotoOutcome(flow, false, "NO_DESCRIPTOR", null, null, null, false, photoBytes.size, quality, startedAt)
            return VerifyFaceResponse(
                matched = false,
                message = "No se pudo generar descriptor facial."
            )
        }

        val photoMatch = findBestMatchWithFallbackCandidates(
            primaryDescriptor = primaryDescriptor,
            photoBytes = photoBytes,
            threshold = faceRecognitionProperties.matching.photoSimilarityThreshold,
            source = flow,
            metric = embeddingMetric(),
            probeQuality = quality.qualityScore
        )
        if (photoMatch == null) {
            logPhotoOutcome(flow, false, "NOT_RECOGNIZED", null, null, primaryDescriptor.size, false, photoBytes.size, quality, startedAt)
            return VerifyFaceResponse(
                matched = false,
                message = "Rostro no reconocido."
            )
        }

        val match = photoMatch.match
        if (isFaceExpired(match.faceCreatedAt)) {
            logPhotoOutcome(flow, false, "EXPIRED", match.user.id, match.score, primaryDescriptor.size, photoMatch.usedFallback, photoBytes.size, quality, startedAt)
            return expiredFaceResponse(match)
        }

        logPhotoOutcome(flow, true, "OK", match.user.id, match.score, primaryDescriptor.size, photoMatch.usedFallback, photoBytes.size, quality, startedAt)
        val occurredAt = occurredAtMillis?.let { Date(it) } ?: Date()

        return when (action) {
            VerifyFaceBody.Action.CHECK_IN -> registerCheckIn(match.user.id, match.userName, match.user, occurredAt, attendanceStatus = attendanceStatus)
            VerifyFaceBody.Action.CHECK_OUT -> registerCheckOut(match.user.id, match.userName, match.user, occurredAt)
        }
    }

    // Sincroniza una marcacion realizada en modo offline.
    // La identidad ya fue validada localmente, por eso registra con auditoria FACE_OFFLINE y respeta la hora real capturada.
    fun verifyAndMarkOffline(body: OfflineAttendanceSyncBody): VerifyFaceResponse {
        if (body.offlineId.isBlank()) {
            return VerifyFaceResponse(
                matched = false,
                message = "Identificador offline vacio."
            )
        }

        // Evita duplicar asistencias si el frontend reintenta sincronizar el mismo pendiente offline.
        attendanceRepository.findByOfflineId(body.offlineId)?.let { existing ->
            return VerifyFaceResponse(
                matched = true,
                userId = existing.user.id,
                userName = existing.user.fullName(),
                userDni = existing.user.dni,
                userType = existing.user.type?.name,
                action = body.action.name,
                message = "Marcacion offline ya sincronizada.",
                checkInTime = formatTime(existing.checkIn),
                attendanceStatus = existing.status,
                nextAction = if (existing.checkOut == null) "CHECK_OUT" else "NONE",
                alreadyRegistered = true
            )
        }

        val user = userRepository.findById(body.userId).orElse(null)
            ?: return VerifyFaceResponse(
                matched = false,
                userId = body.userId,
                message = "Usuario offline no encontrado."
            )

        if (!user.verified || user.type == User.UserType.CLIENT) {
            return VerifyFaceResponse(
                matched = false,
                userId = user.id,
                userName = user.fullName(),
                userDni = user.dni,
                userType = user.type?.name,
                message = "Usuario no habilitado para sincronizar asistencia offline."
            )
        }

        if (body.faceDataId != null) {
            val faceData = faceDataRepository.findById(body.faceDataId).orElse(null)
                ?: return VerifyFaceResponse(
                    matched = false,
                    userId = user.id,
                    userName = user.fullName(),
                    message = "Registro facial offline no encontrado."
                )

            if (faceData.user.id != user.id) {
                return VerifyFaceResponse(
                    matched = false,
                    userId = user.id,
                    userName = user.fullName(),
                    message = "El registro facial offline no pertenece al usuario."
                )
            }
        }

        val occurredAt = Date(body.occurredAtMillis)
        return when (body.action) {
            VerifyFaceBody.Action.CHECK_IN -> registerCheckIn(
                user.id,
                user.fullName(),
                user,
                occurredAt,
                method = "FACE_OFFLINE",
                offlineId = body.offlineId
            )
            VerifyFaceBody.Action.CHECK_OUT -> registerCheckOut(
                user.id,
                user.fullName(),
                user,
                occurredAt,
                method = "FACE_OFFLINE",
                offlineId = body.offlineId
            )
        }
    }

    private fun findBestMatch(
        descriptor: List<Double>,
        threshold: Double,
        source: String = "face-verify",
        metric: FaceComparisonMetric = FaceComparisonMetric.EUCLIDEAN_DISTANCE,
        probeQuality: Double? = null
    ): FaceMatch? {
        val startedAt = System.currentTimeMillis()
        val minMargin = configuredMinMargin(metric)
        val saved = getStoredFaceEmbeddings()
        if (saved.isEmpty()) {
            logger.info("Login facial: no existen registros en face_data para comparar.")
            logMatchMetric(source, metric, threshold, minMargin, "REJECTED", "NO_EMBEDDINGS", descriptor.size, 0, 0, emptyList(), startedAt)
            return null
        }

        val userScores = mutableMapOf<Int, UserFaceScore>()

        for (faceRef in saved) {
            if (faceRef.embedding.size != descriptor.size) continue

            val rawScore = compareFaceDescriptors(faceRef.embedding, descriptor, metric)
            val score = applyProbeQualityWeight(rawScore, metric, probeQuality)
            val currentBest = userScores[faceRef.userId]
            val isBetterUserScore = when (metric) {
                FaceComparisonMetric.EUCLIDEAN_DISTANCE ->
                    currentBest == null || score < currentBest.bestScore
                FaceComparisonMetric.COSINE_SIMILARITY ->
                    currentBest == null || score > currentBest.bestScore
            }

            if (isBetterUserScore) {
                userScores[faceRef.userId] = UserFaceScore(
                    userId = faceRef.userId,
                    bestScore = score,
                    bestFaceRef = faceRef
                )
            }
        }

        if (userScores.isEmpty()) {
            logMatchMetric(source, metric, threshold, minMargin, "REJECTED", "DIMENSION_MISMATCH", descriptor.size, 0, saved.size, emptyList(), startedAt)
            return null
        }

        val rankedUsers = userScores.values.sortedWith { left, right ->
            when (metric) {
                FaceComparisonMetric.EUCLIDEAN_DISTANCE -> left.bestScore.compareTo(right.bestScore)
                FaceComparisonMetric.COSINE_SIMILARITY -> right.bestScore.compareTo(left.bestScore)
            }
        }

        val bestUser = rankedUsers.first()
        val secondBestScore = rankedUsers.getOrNull(1)?.bestScore
            ?: if (metric == FaceComparisonMetric.EUCLIDEAN_DISTANCE) Double.MAX_VALUE else -1.0

        val matchesThreshold = when (metric) {
            FaceComparisonMetric.EUCLIDEAN_DISTANCE -> bestUser.bestScore <= threshold
            FaceComparisonMetric.COSINE_SIMILARITY -> bestUser.bestScore >= threshold
        }
        val marginOk = hasSafeMatchMargin(bestUser.bestScore, secondBestScore, metric)

        val reason = when {
            !matchesThreshold -> "BELOW_THRESHOLD"
            !marginOk -> "LOW_MARGIN"
            else -> "OK"
        }

        if (!matchesThreshold || !marginOk) {
            logger.info(
                "Login facial [{}]: match rechazado por baja confianza. best={}, second={}, threshold={}, metric={}",
                source,
                bestUser.bestScore,
                secondBestScore,
                threshold,
                metric
            )
            logMatchMetric(source, metric, threshold, minMargin, "REJECTED", reason, descriptor.size, rankedUsers.size, saved.size, rankedUsers, startedAt)
            return null
        }

        val user = userRepository.findById(bestUser.userId).orElse(null)
        if (user == null) {
            logMatchMetric(source, metric, threshold, minMargin, "REJECTED", "USER_NOT_FOUND", descriptor.size, rankedUsers.size, saved.size, rankedUsers, startedAt)
            return null
        }

        logMatchMetric(source, metric, threshold, minMargin, "MATCHED", "OK", descriptor.size, rankedUsers.size, saved.size, rankedUsers, startedAt)
        val bestFaceRef = bestUser.bestFaceRef
        return FaceMatch(
            user,
            "${user.name ?: ""} ${user.lastName ?: ""}".trim(),
            bestFaceRef.createdAt,
            bestFaceRef.angle,
            bestUser.bestScore
        )
    }

    // Construye y emite el registro estructurado de una decision de matching.
    private fun logMatchMetric(
        source: String,
        metric: FaceComparisonMetric,
        threshold: Double,
        minMargin: Double,
        decision: String,
        reason: String,
        descriptorSize: Int,
        datasetUsers: Int,
        datasetEmbeddings: Int,
        rankedUsers: List<UserFaceScore>,
        startedAt: Long
    ) {
        val topN = faceRecognitionMetricsLogger.rankedTopN()
        val ranked = rankedUsers.take(topN).map { it.toCandidateMetric() }
        val best = ranked.firstOrNull()
        val second = ranked.getOrNull(1)
        val margin = if (best != null && second != null) {
            when (metric) {
                FaceComparisonMetric.EUCLIDEAN_DISTANCE -> second.score - best.score
                FaceComparisonMetric.COSINE_SIMILARITY -> best.score - second.score
            }
        } else {
            null
        }

        faceRecognitionMetricsLogger.logMatch(
            FaceMatchMetricRecord(
                source = source,
                metric = metric.name,
                threshold = threshold,
                minMargin = minMargin,
                decision = decision,
                reason = reason,
                descriptorSize = descriptorSize,
                datasetUsers = datasetUsers,
                datasetEmbeddings = datasetEmbeddings,
                best = best,
                second = second,
                margin = margin,
                ranked = ranked,
                elapsedMs = System.currentTimeMillis() - startedAt
            )
        )
    }

    private fun logPhotoOutcome(
        flow: String,
        matched: Boolean,
        reason: String,
        userId: Int?,
        score: Double?,
        descriptorSize: Int?,
        usedFallback: Boolean,
        photoBytes: Int,
        quality: FacePhotoQualityService.QualityResult,
        startedAt: Long
    ) {
        faceRecognitionMetricsLogger.logPhotoOutcome(
            FacePhotoOutcomeRecord(
                flow = flow,
                matched = matched,
                reason = reason,
                userId = userId,
                score = score,
                metric = embeddingMetric().name,
                threshold = faceRecognitionProperties.matching.photoSimilarityThreshold,
                descriptorSize = descriptorSize,
                usedFallback = usedFallback,
                photoBytes = photoBytes,
                quality = FaceQualityMetric(
                    valid = quality.valid,
                    width = quality.width,
                    height = quality.height,
                    brightness = quality.brightness,
                    skinRatio = quality.skinRatio,
                    edgeRatio = quality.edgeRatio
                ),
                elapsedMs = System.currentTimeMillis() - startedAt
            )
        )
    }

    private fun configuredMinMargin(metric: FaceComparisonMetric): Double = when (metric) {
        FaceComparisonMetric.EUCLIDEAN_DISTANCE -> faceRecognitionProperties.matching.euclideanMinMargin
        FaceComparisonMetric.COSINE_SIMILARITY -> faceRecognitionProperties.matching.photoMinMargin
    }

    private fun UserFaceScore.toCandidateMetric(): FaceCandidateMetric = FaceCandidateMetric(
        userId = userId,
        score = bestScore,
        angle = bestFaceRef.angle.name,
        faceDataId = bestFaceRef.id
    )

    // Prueba varios descriptores de una misma foto y conserva el match mas confiable.
    private fun findBestMatchFromCandidates(
        descriptors: List<List<Double>>,
        threshold: Double,
        source: String,
        metric: FaceComparisonMetric,
        probeQuality: Double? = null
    ): FaceMatch? {
        var bestCandidateMatch: FaceMatch? = null

        for ((index, descriptor) in descriptors.withIndex()) {
            val match = findBestMatch(
                descriptor = descriptor,
                threshold = threshold,
                source = "$source-candidate-$index",
                metric = metric,
                probeQuality = probeQuality
            )

            if (match != null && isBetterMatch(match, bestCandidateMatch, metric)) {
                bestCandidateMatch = match
            }
        }

        return bestCandidateMatch
    }

    // Camino rapido para marcacion: primero intenta con un solo descriptor.
    // Solo si no hay match confiable usa recortes/candidatos como respaldo.
    private fun findBestMatchWithFallbackCandidates(
        primaryDescriptor: List<Double>,
        photoBytes: ByteArray,
        threshold: Double,
        source: String,
        metric: FaceComparisonMetric,
        probeQuality: Double? = null
    ): PhotoMatch? {
        findBestMatch(
            descriptor = primaryDescriptor,
            threshold = threshold,
            source = "$source-primary",
            metric = metric,
            probeQuality = probeQuality
        )?.let { return PhotoMatch(it, usedFallback = false) }

        val fallbackDescriptors = facePhotoDescriptorService.generateDescriptorCandidates(photoBytes)
            .filterNot { it == primaryDescriptor }

        if (fallbackDescriptors.isEmpty()) return null

        return findBestMatchFromCandidates(
            descriptors = fallbackDescriptors,
            threshold = threshold,
            source = "$source-fallback",
            metric = metric,
            probeQuality = probeQuality
        )?.let { PhotoMatch(it, usedFallback = true) }
    }

    // Metrica configurada para los embeddings generados por foto (DJL / ArcFace).
    private fun embeddingMetric(): FaceComparisonMetric =
        if (faceEmbeddingProperties.metric.equals("EUCLIDEAN_DISTANCE", ignoreCase = true)) {
            FaceComparisonMetric.EUCLIDEAN_DISTANCE
        } else {
            FaceComparisonMetric.COSINE_SIMILARITY
        }

    // Limpia el cache cuando se registra o actualiza un rostro para que el login use el embedding nuevo al instante.
    fun clearFaceEmbeddingCache() {
        synchronized(faceCacheLock) {
            faceEmbeddingCache = emptyList()
            faceEmbeddingCacheLoadedAt = 0
        }
    }

    // Mantiene todos los embeddings parseados por un tiempo corto, incluyendo los angulos FRONT, LEFT y RIGHT.
    private fun getStoredFaceEmbeddings(): List<StoredFaceEmbedding> {
        val now = System.currentTimeMillis()
        val cached = faceEmbeddingCache
        if (cached.isNotEmpty() && now - faceEmbeddingCacheLoadedAt < faceRecognitionProperties.matching.cacheTtlMs) {
            return cached
        }

        return synchronized(faceCacheLock) {
            val current = System.currentTimeMillis()
            val synchronizedCache = faceEmbeddingCache
            if (synchronizedCache.isNotEmpty() && current - faceEmbeddingCacheLoadedAt < faceRecognitionProperties.matching.cacheTtlMs) {
                synchronizedCache
            } else {
                faceDataRepository.findAll()
                    .mapNotNull { faceData -> faceData.toStoredEmbedding() }
                    .let { augmentWithMasterEmbeddings(it) }
                    .also { parsed ->
                        faceEmbeddingCache = parsed
                        faceEmbeddingCacheLoadedAt = current
                    }
            }
        }
    }

    private fun augmentWithMasterEmbeddings(embeddings: List<StoredFaceEmbedding>): List<StoredFaceEmbedding> {
        val byUser = embeddings.groupBy { it.userId }
        val virtualMasters = mutableListOf<StoredFaceEmbedding>()

        for ((userId, userEmbeddings) in byUser) {
            if (userEmbeddings.any { it.angle == FaceAngle.MASTER }) continue

            val angleEmbeddings = userEmbeddings.filter {
                it.angle == FaceAngle.FRONT || it.angle == FaceAngle.LEFT || it.angle == FaceAngle.RIGHT
            }
            if (angleEmbeddings.size < 2) continue

            val master = FaceEmbeddingMath.averageL2Normalized(angleEmbeddings.map { it.embedding }) ?: continue
            val reference = angleEmbeddings.minByOrNull { it.createdAt.time } ?: angleEmbeddings.first()

            virtualMasters.add(
                StoredFaceEmbedding(
                    id = -reference.id,
                    embedding = master,
                    userId = userId,
                    createdAt = reference.createdAt,
                    angle = FaceAngle.MASTER
                )
            )
        }

        return embeddings + virtualMasters
    }

    private fun Face_data.toStoredEmbedding(): StoredFaceEmbedding? {
        val embedding = parseEmbedding(faceEmbedding) ?: return null
        return StoredFaceEmbedding(
            id = id,
            embedding = embedding,
            userId = user.id,
            createdAt = createdAt,
            angle = angle
        )
    }

    private fun applyProbeQualityWeight(
        score: Double,
        metric: FaceComparisonMetric,
        probeQuality: Double?
    ): Double {
        if (probeQuality == null || !faceRecognitionProperties.matching.qualityWeightedMatching) {
            return score
        }

        val weight = faceRecognitionProperties.matching.qualityWeightMin +
            ((1.0 - faceRecognitionProperties.matching.qualityWeightMin) * probeQuality.coerceIn(0.0, 1.0))

        return when (metric) {
            FaceComparisonMetric.COSINE_SIMILARITY -> score * weight
            FaceComparisonMetric.EUCLIDEAN_DISTANCE -> score / weight.coerceAtLeast(0.01)
        }
    }

    private fun challengeRequiredResponse(): VerifyFaceResponse {
        return VerifyFaceResponse(
            matched = false,
            message = "Reto de seguridad requerido. Repite el giro de cabeza para marcar.",
            challengeRequired = true
        )
    }

    private fun expiredFaceResponse(match: FaceMatch): VerifyFaceResponse {
        return VerifyFaceResponse(
            matched = false,
            userId = match.user.id,
            userName = match.userName,
            userDni = match.user.dni,
            userType = match.user.type?.name,
            message = "Revalidacion facial requerida. El registro facial supero los 6 meses.",
            requiresReenrollment = true
        )
    }

    private fun registerCheckIn(
        userId: Int,
        userName: String?,
        user: User,
        occurredAt: Date = Date(),
        method: String = "FACIAL",
        offlineId: String? = null,
        attendanceStatus: String? = null
    ): VerifyFaceResponse {
        val (dayStart, dayEnd) = dayRange(occurredAt)
        val previous = attendanceRepository.findTopByUser_IdAndCheckInBetweenOrderByCheckInDesc(
            userId,
            dayStart,
            dayEnd
        )

        if (previous != null) {
            val nextAction = if (previous.checkOut == null && canRegisterCheckOut(previous.checkIn, occurredAt)) {
                "CHECK_OUT"
            } else {
                "NONE"
            }

            return VerifyFaceResponse(
                matched = true,
                userId = userId,
                userName = userName,
                userDni = user.dni,
                userType = user.type?.name,
                action = "CHECK_IN",
                message = "Ya registraste tu asistencia de hoy.",
                checkInTime = formatTime(previous.checkIn),
                attendanceStatus = previous.status,
                nextAction = nextAction,
                alreadyRegistered = true
            )
        }

        val status = attendanceStatus?.trim()?.takeIf { it.isNotEmpty() } ?: if (isLate(occurredAt)) "TARDANZA" else "OK"
        val attendance = Attendance(
            id = 0,
            checkIn = occurredAt,
            checkOut = null,
            method = method,
            status = status,
            offlineId = offlineId,
            user = user
        )
        attendanceRepository.save(attendance)

        return VerifyFaceResponse(
            matched = true,
            userId = userId,
            userName = userName,
            userDni = user.dni,
            userType = user.type?.name,
            action = "CHECK_IN",
            message = if (status == "TARDANZA") "Asistencia registrada con tardanza." else "Asistencia registrada.",
            checkInTime = formatTime(occurredAt),
            attendanceStatus = status,
            nextAction = "CHECK_OUT",
            alreadyRegistered = false
        )
    }

    private fun registerCheckOut(
        userId: Int,
        userName: String?,
        user: User,
        occurredAt: Date = Date(),
        method: String = "FACIAL",
        offlineId: String? = null
    ): VerifyFaceResponse {
        val open = attendanceRepository.findTopByUser_IdAndCheckOutIsNullOrderByCheckInDesc(userId)
            ?: return VerifyFaceResponse(
                matched = true,
                userId = userId,
                userName = userName,
                userDni = user.dni,
                userType = user.type?.name,
                action = "CHECK_OUT",
                message = "No hay un ingreso abierto para registrar salida."
            )

        if (!canRegisterCheckOut(open.checkIn, occurredAt)) {
            return VerifyFaceResponse(
                matched = true,
                userId = userId,
                userName = userName,
                userDni = user.dni,
                userType = user.type?.name,
                action = "CHECK_OUT",
                message = "La salida ya no esta disponible para ese ingreso. Puedes marcar una nueva asistencia.",
                nextAction = "CHECK_IN"
            )
        }

        open.checkOut = occurredAt
        attendanceRepository.save(open)

        return VerifyFaceResponse(
            matched = true,
            userId = userId,
            userName = userName,
            userDni = user.dni,
            userType = user.type?.name,
            action = "CHECK_OUT",
            message = "Salida registrada.",
            nextAction = "NONE"
        )
    }

    // Define que accion debe pedir la UI segun si el usuario tiene un ingreso abierto.
    private fun nextAttendanceAction(userId: Int): String {
        val open = attendanceRepository.findTopByUser_IdAndCheckOutIsNullOrderByCheckInDesc(userId)
        return if (open != null && canRegisterCheckOut(open.checkIn, Date())) "CHECK_OUT" else "CHECK_IN"
    }

    // La salida solo se ofrece si el ingreso abierto es de hoy y aun no pasan las 6:00 PM.
    private fun canRegisterCheckOut(checkIn: Date, now: Date): Boolean {
        val (dayStart, dayEnd) = dayRange(now)
        if (checkIn.before(dayStart) || checkIn.after(dayEnd)) return false

        val limit = Calendar.getInstance().apply {
            time = now
            set(Calendar.HOUR_OF_DAY, CHECK_OUT_LIMIT_HOUR)
            set(Calendar.MINUTE, CHECK_OUT_LIMIT_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return !now.after(limit.time)
    }

    private fun parseEmbedding(json: String): List<Double>? {
        return try {
            objectMapper.readValue(json, object : TypeReference<List<Double>>() {})
        } catch (e: Exception) {
            null
        }
    }

    private fun euclideanDistance(a: List<Double>, b: List<Double>): Double {
        var sum = 0.0
        for (i in a.indices) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum)
    }

    // DJL/FaceNet recomienda comparar embeddings con similitud coseno.
    private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        if (normA == 0.0 || normB == 0.0) return 0.0
        return ((dot / sqrt(normA) / sqrt(normB)) + 1.0) / 2.0
    }

    // Mantiene la metrica anterior para los endpoints existentes y usa coseno solo en login DJL.
    private fun compareFaceDescriptors(
        stored: List<Double>,
        current: List<Double>,
        metric: FaceComparisonMetric
    ): Double {
        return when (metric) {
            FaceComparisonMetric.EUCLIDEAN_DISTANCE -> euclideanDistance(stored, current)
            FaceComparisonMetric.COSINE_SIMILARITY -> cosineSimilarity(stored, current)
        }
    }

    // Usa el descriptor principal y tambien recortes alternativos para mejorar la identificacion
    // cuando el rostro no queda perfectamente centrado en la marcacion.
    private fun generateFastPhotoDescriptors(photoBytes: ByteArray): List<List<Double>> {
        val descriptors = mutableListOf<List<Double>>()

        facePhotoDescriptorService.generateDescriptor(photoBytes)?.let { descriptor ->
            descriptors.add(descriptor)
        }

        facePhotoDescriptorService.generateDescriptorCandidates(photoBytes).forEach { candidate ->
            if (descriptors.none { it == candidate }) {
                descriptors.add(candidate)
            }
        }

        return descriptors
    }

    private fun isSecondBest(score: Double, secondBestScore: Double, metric: FaceComparisonMetric): Boolean {
        return when (metric) {
            FaceComparisonMetric.EUCLIDEAN_DISTANCE -> score < secondBestScore
            FaceComparisonMetric.COSINE_SIMILARITY -> score > secondBestScore
        }
    }

    // Evita asignar una asistencia si dos rostros guardados son demasiado parecidos para decidir con seguridad.
    private fun hasSafeMatchMargin(bestScore: Double, secondBestScore: Double, metric: FaceComparisonMetric): Boolean {
        return when (metric) {
            FaceComparisonMetric.EUCLIDEAN_DISTANCE ->
                secondBestScore == Double.MAX_VALUE || secondBestScore - bestScore >= faceRecognitionProperties.matching.euclideanMinMargin
            FaceComparisonMetric.COSINE_SIMILARITY ->
                secondBestScore == -1.0 || bestScore - secondBestScore >= faceRecognitionProperties.matching.photoMinMargin
        }
    }

    private fun isBetterMatch(candidate: FaceMatch, current: FaceMatch?, metric: FaceComparisonMetric): Boolean {
        if (current == null) return true

        return when (metric) {
            FaceComparisonMetric.EUCLIDEAN_DISTANCE -> candidate.score < current.score
            FaceComparisonMetric.COSINE_SIMILARITY -> candidate.score > current.score
        }
    }

    private fun dayRange(date: Date): Pair<Date, Date> {
        val start = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = Calendar.getInstance().apply {
            time = start.time
            add(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MILLISECOND, -1)
        }
        return Pair(start.time, end.time)
    }

    private fun isLate(date: Date): Boolean {
        val limit = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, CHECK_IN_LIMIT_HOUR)
            set(Calendar.MINUTE, CHECK_IN_LIMIT_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, CHECK_IN_TOLERANCE_MINUTES)
        }
        return date.after(limit.time)
    }

    private fun isFaceExpired(createdAt: Date): Boolean {
        val limit = Calendar.getInstance().apply {
            time = createdAt
            add(Calendar.MONTH, FACE_REVALIDATION_MONTHS)
        }
        return Date().after(limit.time)
    }

    private fun formatTime(date: Date): String {
        return SimpleDateFormat("h:mm a", Locale.US).format(date).replace(" ", "")
    }

    private fun User.fullName(): String {
        return "${name ?: ""} ${lastName ?: ""}".trim().ifBlank { username ?: "Usuario" }
    }

    private data class FaceMatch(
        val user: User,
        val userName: String,
        val faceCreatedAt: Date,
        val angle: FaceAngle,
        val score: Double
    )

    private data class PhotoMatch(
        val match: FaceMatch,
        val usedFallback: Boolean
    )

    private data class StoredFaceEmbedding(
        val id: Int,
        val embedding: List<Double>,
        val userId: Int,
        val createdAt: Date,
        val angle: FaceAngle
    )

    private data class UserFaceScore(
        val userId: Int,
        val bestScore: Double,
        val bestFaceRef: StoredFaceEmbedding
    )

    private enum class FaceComparisonMetric {
        EUCLIDEAN_DISTANCE,
        COSINE_SIMILARITY
    }
    // agregue  metodo esto reutiliza la comparacion actual de face_Data pero sin registrar asistencia 15/05/2026
    fun indentifyUserForLogin(descriptor: List<Double>): User? {
        if (descriptor.isEmpty()) {
            return null
        }

        val match = findBestMatch(
            descriptor = descriptor,
            threshold = faceRecognitionProperties.matching.photoSimilarityThreshold,
            source = "login-photo-djl",
            metric = FaceComparisonMetric.COSINE_SIMILARITY
        ) ?: return null

        if (isFaceExpired(match.faceCreatedAt)) {
            logger.info("Login facial por foto: rostro reconocido, pero el registro facial esta vencido. userId={}, angle={}", match.user.id, match.angle)
            return null
        }
        return  match.user
    }

    // Login facial por foto: genera el descriptor en backend y reutiliza la comparacion actual.
    fun identifyUserFromPhotoForLogin(photo: MultipartFile): User? {
        if (photo.isEmpty) {
            return null
        }

        val photoBytes = photo.bytes
        val quality = facePhotoQualityService.evaluate(photoBytes)
        if (!quality.valid) {
            return null
        }

        val descriptors = facePhotoDescriptorService.generateDescriptorCandidates(photoBytes)
        return findBestMatchFromCandidates(
            descriptors = descriptors,
            threshold = faceRecognitionProperties.matching.photoSimilarityThreshold,
            source = "login-photo-djl",
            metric = FaceComparisonMetric.COSINE_SIMILARITY,
            probeQuality = quality.qualityScore
        )?.user
    }
}
