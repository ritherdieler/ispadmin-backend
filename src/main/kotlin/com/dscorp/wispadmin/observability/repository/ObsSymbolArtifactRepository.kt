package com.dscorp.wispadmin.observability.repository

import com.dscorp.wispadmin.observability.entity.ObsSymbolArtifact
import com.dscorp.wispadmin.observability.entity.ObsSymbolArtifactType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ObsSymbolArtifactRepository : JpaRepository<ObsSymbolArtifact, Long> {

    fun findAllByOrderByUploadedAtDesc(): List<ObsSymbolArtifact>

    fun findByPlatformAndReleaseAndTypeAndBundle(
        platform: String,
        release: String,
        type: ObsSymbolArtifactType,
        bundle: String
    ): ObsSymbolArtifact?

    fun findFirstByPlatformAndReleaseAndTypeOrderByUploadedAtDesc(
        platform: String,
        release: String,
        type: ObsSymbolArtifactType
    ): ObsSymbolArtifact?

    fun findByUploadedAtBefore(threshold: LocalDateTime): List<ObsSymbolArtifact>

    @Query("select count(a) from ObsSymbolArtifact a where a.type = :type")
    fun countByType(@Param("type") type: ObsSymbolArtifactType): Long
}
