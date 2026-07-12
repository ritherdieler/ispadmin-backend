package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsReplay
import com.dscorp.wispadmin.observability.repository.ObsReplayRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class ObsReplayService(
    private val replayRepository: ObsReplayRepository,
    private val properties: ObservabilityProperties
) {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val dayFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

    fun store(
        sessionId: String?,
        eventId: Long?,
        issueId: Long?,
        platform: String?,
        format: String?,
        durationMs: Long?,
        contentEncoding: String?,
        data: ByteArray
    ): ObsReplay {
        val now = LocalDateTime.now()
        val subDir = File(properties.replay.storageDir, now.format(dayFormatter))
        if (!subDir.exists()) subDir.mkdirs()

        val resolvedFormat = format?.takeIf { it.isNotBlank() } ?: "rrweb"
        val fileName = "${UUID.randomUUID()}.$resolvedFormat.gz"
        val target = File(subDir, fileName)
        Files.write(target.toPath(), data)

        val replay = ObsReplay(
            sessionId = sessionId,
            eventId = eventId,
            issueId = issueId,
            platform = platform,
            format = resolvedFormat,
            filePath = target.absolutePath,
            contentEncoding = contentEncoding ?: "gzip",
            sizeBytes = data.size.toLong(),
            durationMs = durationMs,
            createdAt = now
        )
        return replayRepository.save(replay)
    }

    fun findMetadata(id: Long): ObsReplay? = replayRepository.findById(id).orElse(null)

    fun readBlob(replay: ObsReplay): ByteArray? {
        val path = replay.filePath ?: return null
        val file = Paths.get(path)
        if (!Files.exists(file)) return null
        return Files.readAllBytes(file)
    }

    fun deleteFile(replay: ObsReplay) {
        try {
            replay.filePath?.let {
                val file = Paths.get(it)
                if (Files.exists(file)) Files.delete(file)
            }
        } catch (e: Exception) {
            log.warn("No se pudo borrar el archivo de replay {}: {}", replay.id, e.message)
        }
    }
}
