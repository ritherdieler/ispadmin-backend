package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "face.recognition")
class FaceRecognitionProperties {
    var detector: Detector = Detector()
    var quality: Quality = Quality()
    var matching: Matching = Matching()
    var metrics: Metrics = Metrics()
    var liveness: Liveness = Liveness()

    class Detector {
        var confidenceThreshold: Double = 0.70
        var nmsThreshold: Double = 0.45
        var faceMarginRatio: Double = 0.20
        var minFaceWidthRatio: Double = 0.15
    }

    class Quality {
        var minImageSize: Int = 160
        var minBrightness: Double = 20.0
        var maxBrightness: Double = 230.0
        var minSkinRatio: Double = 0.008
        var minEdgeRatio: Double = 0.005
    }

    class Matching {
        var photoSimilarityThreshold: Double = 0.78
        var photoMinMargin: Double = 0.03
        var offlineThreshold: Double = 0.82
        var offlineMinMargin: Double = 0.04
        var euclideanThreshold: Double = 0.48
        var euclideanMinMargin: Double = 0.06
        var cacheTtlMs: Long = 60_000L
        var offlineMetric: String = "COSINE_SIMILARITY"
        var offlineDescriptorSize: Int = 512
        var qualityWeightedMatching: Boolean = true
        var qualityWeightMin: Double = 0.85
    }

    class Liveness {
        var enabled: Boolean = true
        var minLaplacianVariance: Double = 35.0
        var minColorVariance: Double = 12.0
        // Deteccion de pantalla/moire (opt-in): se entrega lista pero apagada para no introducir
        // falsos rechazos en produccion hasta calibrar el umbral con datos reales.
        var screenDetectionEnabled: Boolean = false
        var maxMoireRatio: Double = 0.50
    }

    class Metrics {
        var enabled: Boolean = true
        var rankedTopN: Int = 5
    }
}
