package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "face.embedding")
class FaceEmbeddingProperties {
    var engine: String = "PyTorch"
    var modelPath: String = "classpath:models/face_feature.zip"
    var modelName: String = "face_feature"
    var inputSize: Int = 224
    var alignment: String = "LEGACY"
    var normalize: String = "0.498,0.498,0.498,0.502,0.502,0.502"
    var metric: String = "COSINE_SIMILARITY"
    var l2Normalize: Boolean = false

    fun isArcFaceAlignment(): Boolean = alignment.equals("ARCFACE", ignoreCase = true)
}
