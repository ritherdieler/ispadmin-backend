package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.FaceRecognitionProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Emite registros estructurados (JSON) del proceso de reconocimiento facial.
 *
 * Objetivo: que una IA u otra herramienta pueda analizar despues estos registros
 * para proponer mejoras de precision (ajuste de umbral/margen, deteccion de confusiones
 * entre usuarios parecidos, calidad de foto, uso de fallback, etc.).
 *
 * Cada linea se emite bajo el logger "FACE_RECOGNITION_METRICS" y puede enrutarse a un
 * archivo dedicado via logback sin mezclarse con el resto de logs de la aplicacion.
 */
@Service
class FaceRecognitionMetricsLogger(
    private val objectMapper: ObjectMapper,
    private val faceRecognitionProperties: FaceRecognitionProperties
) {
    private val metricsLogger = LoggerFactory.getLogger(METRICS_LOGGER_NAME)

    fun rankedTopN(): Int = faceRecognitionProperties.metrics.rankedTopN.coerceAtLeast(1)

    fun logMatch(record: FaceMatchMetricRecord) {
        if (!faceRecognitionProperties.metrics.enabled) return
        runCatching {
            metricsLogger.info(objectMapper.writeValueAsString(record))
        }.onFailure {
            metricsLogger.debug("No se pudo serializar el registro de match facial: {}", it.message)
        }
    }

    fun logPhotoOutcome(record: FacePhotoOutcomeRecord) {
        if (!faceRecognitionProperties.metrics.enabled) return
        runCatching {
            metricsLogger.info(objectMapper.writeValueAsString(record))
        }.onFailure {
            metricsLogger.debug("No se pudo serializar el registro de outcome facial: {}", it.message)
        }
    }

    companion object {
        const val METRICS_LOGGER_NAME = "FACE_RECOGNITION_METRICS"
    }
}

/**
 * Registro de una decision de matching (un descriptor comparado contra face_data).
 */
data class FaceMatchMetricRecord(
    val event: String = "face_match",
    val timestampMillis: Long = System.currentTimeMillis(),
    val source: String,
    val metric: String,
    val threshold: Double,
    val minMargin: Double,
    val decision: String,
    val reason: String,
    val descriptorSize: Int,
    val datasetUsers: Int,
    val datasetEmbeddings: Int,
    val best: FaceCandidateMetric?,
    val second: FaceCandidateMetric?,
    val margin: Double?,
    val ranked: List<FaceCandidateMetric>,
    val elapsedMs: Long
)

/**
 * Resultado completo de un intento de reconocimiento por foto (incluye calidad de imagen).
 */
data class FacePhotoOutcomeRecord(
    val event: String = "face_photo_outcome",
    val timestampMillis: Long = System.currentTimeMillis(),
    val flow: String,
    val matched: Boolean,
    val reason: String,
    val userId: Int?,
    val score: Double?,
    val metric: String,
    val threshold: Double,
    val descriptorSize: Int?,
    val usedFallback: Boolean,
    val photoBytes: Int,
    val quality: FaceQualityMetric?,
    val elapsedMs: Long
)

data class FaceCandidateMetric(
    val userId: Int,
    val score: Double,
    val angle: String,
    val faceDataId: Int
)

data class FaceQualityMetric(
    val valid: Boolean,
    val width: Int,
    val height: Int,
    val brightness: Double,
    val skinRatio: Double,
    val edgeRatio: Double
)
