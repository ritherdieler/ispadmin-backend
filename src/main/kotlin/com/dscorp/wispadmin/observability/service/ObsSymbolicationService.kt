package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsEvent
import com.dscorp.wispadmin.observability.entity.ObsSymbolArtifact
import com.dscorp.wispadmin.observability.service.symbolication.ProguardMapping
import com.dscorp.wispadmin.observability.service.symbolication.SourceMapConsumer
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class ObsSymbolicationService(
    private val artifactService: ObsSymbolArtifactService,
    private val properties: ObservabilityProperties,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val sourceMapCache = ConcurrentHashMap<String, SourceMapConsumer>()
    private val mappingCache = ConcurrentHashMap<String, ProguardMapping>()

    fun evictCaches() {
        sourceMapCache.clear()
        mappingCache.clear()
    }

    /** Returns the symbolicated stacktrace, or null when it cannot be resolved / unchanged. */
    fun symbolicate(event: ObsEvent): String? {
        if (!properties.symbols.enabled) return null
        val stacktrace = event.stacktrace?.takeIf { it.isNotBlank() } ?: return null
        val platform = event.platform?.takeIf { it.isNotBlank() } ?: return null
        val release = event.release?.takeIf { it.isNotBlank() } ?: return null

        return try {
            when {
                platform.startsWith("web", ignoreCase = true) ->
                    symbolicateWeb(platform, release, stacktrace)
                platform.startsWith("android", ignoreCase = true) ->
                    symbolicateAndroid(platform, release, stacktrace)
                else -> null
            }
        } catch (e: Exception) {
            log.warn("Fallo al simbolizar evento {} (platform={}, release={}): {}", event.id, platform, release, e.message)
            null
        }
    }

    private fun symbolicateWeb(platform: String, release: String, stacktrace: String): String? {
        var changed = false
        val consumers = HashMap<String, SourceMapConsumer?>()
        val result = stacktrace.lineSequence().joinToString("\n") { line ->
            val match = WEB_FRAME.find(line) ?: return@joinToString line
            val path = match.groupValues[1]
            val lineNo = match.groupValues[2].toIntOrNull() ?: return@joinToString line
            val colNo = match.groupValues[3].toIntOrNull() ?: return@joinToString line
            val bundle = path.substringAfterLast('/')

            val consumer = consumers.getOrPut(bundle) { loadSourceMap(platform, release, bundle) }
                ?: return@joinToString line
            val original = consumer.originalPositionFor(lineNo, colNo) ?: return@joinToString line

            changed = true
            val nameSuffix = original.name?.let { " $it" } ?: ""
            "$line  →${nameSuffix} ${original.source ?: "?"}:${original.line}:${original.column}"
        }
        return if (changed) result else null
    }

    private fun symbolicateAndroid(platform: String, release: String, stacktrace: String): String? {
        val artifact = artifactService.findMapping(platform, release) ?: return null
        val mapping = loadMapping(artifact) ?: return null

        var changed = false
        val result = stacktrace.lineSequence().joinToString("\n") { line ->
            val match = ANDROID_FRAME.find(line) ?: return@joinToString line
            val obfClass = match.groupValues[1]
            val obfMethod = match.groupValues[2].takeIf { it.isNotBlank() }
            val obfLine = match.groupValues[4].toIntOrNull()

            val retraced = mapping.retrace(obfClass, obfMethod, obfLine) ?: return@joinToString line
            changed = true
            val method = retraced.originalMethod ?: obfMethod ?: ""
            val lineSuffix = retraced.originalLine?.let { ":$it" } ?: ""
            "$line  →  ${retraced.originalClass}.$method$lineSuffix"
        }
        return if (changed) result else null
    }

    private fun loadSourceMap(platform: String, release: String, bundle: String): SourceMapConsumer? {
        val artifact = artifactService.findSourceMap(platform, release, bundle) ?: return null
        val path = artifact.filePath ?: return null
        sourceMapCache[path]?.let { return it }

        val content = artifactService.readText(artifact) ?: return null
        val consumer = parseSourceMap(content) ?: return null
        sourceMapCache[path] = consumer
        return consumer
    }

    private fun parseSourceMap(content: String): SourceMapConsumer? {
        val node = objectMapper.readTree(content)
        val mappings = node.path("mappings").asText(null) ?: return null
        val sources = node.path("sources").mapNotNull { it.asText(null) }
        val names = node.path("names").mapNotNull { it.asText(null) }
        val sourceRoot = node.path("sourceRoot").asText(null)
        return SourceMapConsumer(sources, names, mappings, sourceRoot)
    }

    private fun loadMapping(artifact: ObsSymbolArtifact): ProguardMapping? {
        val path = artifact.filePath ?: return null
        mappingCache[path]?.let { return it }
        val content = artifactService.readText(artifact) ?: return null
        val mapping = ProguardMapping.parse(content)
        mappingCache[path] = mapping
        return mapping
    }

    companion object {
        private val WEB_FRAME = Regex("([\\w.\\-/@]+\\.(?:js|mjs|cjs)):(\\d+):(\\d+)")
        private val ANDROID_FRAME = Regex("at\\s+([\\w$.]+)\\.([\\w$<>]+)\\(([^):]*)(?::(\\d+))?\\)")
    }
}
