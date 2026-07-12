package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.config.ObservabilityApiKeyFilter
import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsSymbolArtifact
import com.dscorp.wispadmin.observability.entity.ObsSymbolArtifactType
import com.dscorp.wispadmin.observability.service.ObsSymbolArtifactService
import com.dscorp.wispadmin.observability.service.ObsSymbolicationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.util.StreamUtils
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/observability/symbols")
class ObservabilitySymbolController(
    private val artifactService: ObsSymbolArtifactService,
    private val symbolicationService: ObsSymbolicationService,
    private val properties: ObservabilityProperties
) {

    @PostMapping("/sourcemaps")
    fun uploadSourceMap(
        @RequestParam("release", required = false) release: String?,
        @RequestParam("bundle", required = false) bundle: String?,
        @RequestParam("platform", required = false) platform: String?,
        @RequestParam("file", required = false) file: MultipartFile?,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        if (!enabled()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()

        val resolvedPlatform = resolvePlatform(platform, request)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "platform_required"))
        val resolvedRelease = release?.takeIf { it.isNotBlank() }
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "release_required"))
        val resolvedBundle = bundle?.takeIf { it.isNotBlank() }
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "bundle_required"))

        val data = readBody(file, request) ?: return ResponseEntity.badRequest().body(mapOf("error" to "empty_body"))
        val tooLarge = validateSize(data)
        if (tooLarge != null) return tooLarge

        val artifact = artifactService.store(
            platform = resolvedPlatform,
            release = resolvedRelease,
            type = ObsSymbolArtifactType.SOURCE_MAP,
            bundle = resolvedBundle,
            originalName = file?.originalFilename,
            data = data
        )
        symbolicationService.evictCaches()
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(artifact))
    }

    @PostMapping("/proguard")
    fun uploadProguard(
        @RequestParam("release", required = false) release: String?,
        @RequestParam("platform", required = false) platform: String?,
        @RequestParam("file", required = false) file: MultipartFile?,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        if (!enabled()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()

        val resolvedPlatform = resolvePlatform(platform, request) ?: "android"
        val resolvedRelease = release?.takeIf { it.isNotBlank() }
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "release_required"))

        val data = readBody(file, request) ?: return ResponseEntity.badRequest().body(mapOf("error" to "empty_body"))
        val tooLarge = validateSize(data)
        if (tooLarge != null) return tooLarge

        val artifact = artifactService.store(
            platform = resolvedPlatform,
            release = resolvedRelease,
            type = ObsSymbolArtifactType.PROGUARD_MAPPING,
            bundle = null,
            originalName = file?.originalFilename,
            data = data
        )
        symbolicationService.evictCaches()
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(artifact))
    }

    @GetMapping
    fun list(): ResponseEntity<List<Map<String, Any?>>> {
        if (!enabled()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        return ResponseEntity.ok(artifactService.list().map { toResponse(it) })
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        if (!enabled()) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        val deleted = artifactService.delete(id)
        symbolicationService.evictCaches()
        return if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    private fun enabled(): Boolean = properties.enabled && properties.symbols.enabled

    private fun resolvePlatform(explicit: String?, request: HttpServletRequest): String? {
        explicit?.takeIf { it.isNotBlank() }?.let { return it }
        return (request.getAttribute(ObservabilityApiKeyFilter.PLATFORM_ATTRIBUTE) as? String)?.takeIf { it.isNotBlank() }
    }

    private fun readBody(file: MultipartFile?, request: HttpServletRequest): ByteArray? {
        val data: ByteArray = when {
            file != null && !file.isEmpty -> file.bytes
            else -> StreamUtils.copyToByteArray(request.inputStream)
        }
        return data.takeIf { it.isNotEmpty() }
    }

    private fun validateSize(data: ByteArray): ResponseEntity<Map<String, Any?>>? {
        if (data.size > properties.symbols.maxUploadBytes) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(mapOf("error" to "payload_too_large", "maxBytes" to properties.symbols.maxUploadBytes))
        }
        return null
    }

    private fun toResponse(artifact: ObsSymbolArtifact): Map<String, Any?> = mapOf(
        "id" to artifact.id,
        "platform" to artifact.platform,
        "release" to artifact.release,
        "type" to artifact.type.name,
        "bundle" to artifact.bundle,
        "fileName" to artifact.fileName,
        "checksum" to artifact.checksum,
        "sizeBytes" to artifact.sizeBytes,
        "uploadedAt" to artifact.uploadedAt
    )
}
