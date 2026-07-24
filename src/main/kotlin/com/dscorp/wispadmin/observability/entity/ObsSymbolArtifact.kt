package com.dscorp.wispadmin.observability.entity

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

enum class ObsSymbolArtifactType {
    SOURCE_MAP,
    PROGUARD_MAPPING
}

@Entity
@Table(
    name = "obs_symbol_artifact",
    indexes = [
        Index(name = "idx_obs_symbol_lookup", columnList = "platform,app_release,type"),
        Index(name = "idx_obs_symbol_bundle", columnList = "bundle"),
        Index(name = "idx_obs_symbol_uploaded_at", columnList = "uploaded_at")
    ]
)
data class ObsSymbolArtifact(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "platform", length = 60)
    var platform: String? = null,

    @Column(name = "app_release", length = 120)
    var release: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    var type: ObsSymbolArtifactType = ObsSymbolArtifactType.SOURCE_MAP,

    @Column(name = "bundle", length = 300)
    var bundle: String? = null,

    @Column(name = "file_name", length = 300)
    var fileName: String? = null,

    @Column(name = "file_path", length = 500)
    var filePath: String? = null,

    @Column(name = "checksum", length = 80)
    var checksum: String? = null,

    @Column(name = "size_bytes")
    var sizeBytes: Long? = null,

    @Column(name = "uploaded_at")
    var uploadedAt: LocalDateTime? = null
)
