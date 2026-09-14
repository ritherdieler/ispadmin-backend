package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsSymbolArtifact
import com.dscorp.wispadmin.observability.entity.ObsSymbolArtifactType
import com.dscorp.wispadmin.observability.repository.ObsSymbolArtifactRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class ObsSymbolArtifactService(
    private val artifactRepository: ObsSymbolArtifactRepository,
    private val properties: ObservabilityProperties
) {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val dayFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

    fun store(
        platform: String,
        release: String,
        type: ObsSymbolArtifactType,
        bundle: String?,
        originalName: String?,
        data: ByteArray
    ): ObsSymbolArtifact {
        val now = LocalDateTime.now()
        val subDir = File(properties.symbols.storageDir, now.format(dayFormatter))
        if (!subDir.exists()) subDir.mkdirs()

        val extension = if (type == ObsSymbolArtifactType.SOURCE_MAP) "map" else "txt"
        val fileName = "${UUID.randomUUID()}.$extension"
        val target = File(subDir, fileName)
        Files.write(target.toPath(), data)

        val existing = if (type == ObsSymbolArtifactType.SOURCE_MAP && bundle != null) {
            artifactRepository.findByPlatformAndReleaseAndTypeAndBundle(platform, release, type, bundle)
        } else {
            artifactRepository.findFirstByPlatformAndReleaseAndTypeOrderByUploadedAtDesc(platform, release, type)
        }
        val artifact = (existing ?: ObsSymbolArtifact()).apply {
            this.platform = platform
            this.release = release
            this.type = type
            this.bundle = bundle
            this.fileName = originalName
            this.checksum = sha256(data)
            this.sizeBytes = data.size.toLong()
            this.uploadedAt = now
        }
        existing?.filePath?.let { deletePath(it) }
        artifact.filePath = target.absolutePath
        return artifactRepository.save(artifact)
    }

    fun findSourceMap(platform: String, release: String, bundle: String): ObsSymbolArtifact? =
        artifactRepository.findByPlatformAndReleaseAndTypeAndBundle(
            platform, release, ObsSymbolArtifactType.SOURCE_MAP, bundle
        )

    fun findMapping(platform: String, release: String): ObsSymbolArtifact? =
        artifactRepository.findFirstByPlatformAndReleaseAndTypeOrderByUploadedAtDesc(
            platform, release, ObsSymbolArtifactType.PROGUARD_MAPPING
        )

    fun readText(artifact: ObsSymbolArtifact): String? {
        val path = artifact.filePath ?: return null
        val file = Paths.get(path)
        if (!Files.exists(file)) return null
        return String(Files.readAllBytes(file), Charsets.UTF_8)
    }

    fun list(): List<ObsSymbolArtifact> = artifactRepository.findAllByOrderByUploadedAtDesc()

    fun delete(id: Long): Boolean {
        val artifact = artifactRepository.findById(id).orElse(null) ?: return false
        artifact.filePath?.let { deletePath(it) }
        artifactRepository.delete(artifact)
        return true
    }

    fun deleteFile(artifact: ObsSymbolArtifact) {
        artifact.filePath?.let { deletePath(it) }
    }

    private fun deletePath(path: String) {
        try {
            val file = Paths.get(path)
            if (Files.exists(file)) Files.delete(file)
        } catch (e: Exception) {
            log.warn("No se pudo borrar el artefacto de simbolos en {}: {}", path, e.message)
        }
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
