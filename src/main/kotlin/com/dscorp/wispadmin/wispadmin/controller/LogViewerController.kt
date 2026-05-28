package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.data.model.ErrorLog
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

data class LogFileDTO(
    val name: String,
    val size: String,
    val lastModified: String
)

data class LogFilesResponseDTO(
    val success: Boolean,
    val message: String? = null,
    val files: List<LogFileDTO> = emptyList()
)

data class LogContentResponseDTO(
    val success: Boolean,
    val fileName: String? = null,
    val totalLines: Int? = null,
    val linesShown: Int? = null,
    val content: List<String> = emptyList(),
    val message: String? = null
)

data class LogSearchResponseDTO(
    val success: Boolean,
    val fileName: String? = null,
    val totalResults: Int? = null,
    val results: List<String> = emptyList(),
    val message: String? = null
)

data class ModulesResponseDTO(
    val success: Boolean,
    val modules: Set<String> = emptySet()
)

data class ErrorLogDTO(
    val id: Int?,
    val module: String?,
    val error: String?,
    val data: String?,
    val date: Date
)

data class DbErrorsResponseDTO(
    val success: Boolean,
    val errors: List<ErrorLogDTO> = emptyList(),
    val total: Long = 0,
    val page: Int = 0,
    val size: Int = 0,
    val totalPages: Int = 0,
    val message: String? = null
)

data class ModuleStatDTO(
    val module: String,
    val count: Long
)

data class DbStatsResponseDTO(
    val success: Boolean,
    val total: Long = 0,
    val today: Long = 0,
    val lastHour: Long = 0,
    val byModule: List<ModuleStatDTO> = emptyList(),
    val message: String? = null
)

data class HttpFailureDTO(
    val ts: String,
    val id: String,
    val method: String,
    val uri: String,
    val query: String,
    val status: Int,
    val ms: Long,
    val ip: String,
    val contentType: String,
    val userAgent: String,
    val authorization: String,
    val reqBody: String,
    val resBody: String,
    val error: String,
    val stackTraceSummary: String = ""
)

data class HttpFailuresResponseDTO(
    val success: Boolean,
    val failures: List<HttpFailureDTO> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val size: Int = 0,
    val totalPages: Int = 0,
    val message: String? = null
)

@RestController
@RequestMapping("/api/logs")
class LogViewerController @Autowired constructor(
    private val errorLogRepository: ErrorLogRepository,
    private val objectMapper: ObjectMapper
) {

    @Value("\${log.dir:\${LOG_DIR:./logs/wispadmin}}")
    private lateinit var logDir: String

    @GetMapping("/files")
    fun getLogFiles(): LogFilesResponseDTO {
        val logDirectory = File(logDir)
        if (!logDirectory.exists() || !logDirectory.isDirectory) {
            return LogFilesResponseDTO(
                success = false,
                message = "Directorio de logs no encontrado: $logDir"
            )
        }

        val files = logDirectory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?.map {
                LogFileDTO(
                    name = it.name,
                    size = "${it.length() / 1024} KB",
                    lastModified = Date(it.lastModified()).toString()
                )
            } ?: emptyList()

        return LogFilesResponseDTO(success = true, files = files)
    }

    @GetMapping("/{fileName}")
    fun viewLog(
        @PathVariable fileName: String,
        @RequestParam(required = false, defaultValue = "100") lines: Int
    ): LogContentResponseDTO {
        if (fileName.contains("..") || !fileName.endsWith(".log")) {
            return LogContentResponseDTO(success = false, message = "Nombre de archivo inválido")
        }

        val file = File("$logDir/$fileName")
        if (!file.exists() || !file.isFile) {
            return LogContentResponseDTO(success = false, message = "Archivo no encontrado: $logDir/$fileName")
        }

        return try {
            val allLines = file.readLines()
            val logContent = if (allLines.size > lines) allLines.takeLast(lines).reversed() else allLines.reversed()
            LogContentResponseDTO(
                success = true,
                fileName = fileName,
                totalLines = allLines.size,
                linesShown = logContent.size,
                content = logContent
            )
        } catch (e: Exception) {
            LogContentResponseDTO(success = false, message = "Error al leer el archivo: ${e.message}")
        }
    }

    @GetMapping("/errors")
    fun getRecentErrors(
        @RequestParam(required = false, defaultValue = "50") lines: Int,
        @RequestParam(required = false) fileName: String?
    ): LogContentResponseDTO {
        val requestedFile = fileName?.takeIf { it.endsWith(".log") && !it.contains("..") }
        if (requestedFile != null) {
            return viewLog(requestedFile, lines)
        }

        val logDirectory = File(logDir)
        if (!logDirectory.exists() || !logDirectory.isDirectory) {
            return LogContentResponseDTO(
                success = false,
                message = "Directorio de logs no encontrado: $logDir"
            )
        }

        val detectedErrorFile = logDirectory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".log") && it.name.contains("error", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.firstOrNull()
            ?.name

        if (detectedErrorFile != null) {
            return viewLog(detectedErrorFile, lines)
        }

        val newestLogFile = logDirectory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?.firstOrNull()
            ?.name

        if (newestLogFile != null) {
            return viewLog(newestLogFile, lines)
        }

        return LogContentResponseDTO(
            success = false,
            message = "No se encontraron archivos .log en $logDir"
        )
    }

    @GetMapping("/modules")
    fun getModules(): ModulesResponseDTO {
        val allModulesFromEnum = Modules.values().map { it.name }
        val modulesWithRecords = errorLogRepository.findDistinctModules()
        val validModules = (allModulesFromEnum + modulesWithRecords).toSet()
        return ModulesResponseDTO(success = true, modules = validModules)
    }

    @GetMapping("/search")
    fun searchLogs(
        @RequestParam file: String,
        @RequestParam(required = false) searchTerm: String?,
        @RequestParam(required = false) module: String?,
        @RequestParam(required = false) level: String?
    ): LogSearchResponseDTO {
        if (file.contains("..") || !file.endsWith(".log")) {
            return LogSearchResponseDTO(success = false, message = "Nombre de archivo inválido")
        }

        val logFile = File("$logDir/$file")
        if (!logFile.exists() || !logFile.isFile) {
            return LogSearchResponseDTO(success = false, message = "Archivo no encontrado: $logDir/$file")
        }

        return try {
            val lines = logFile.readLines()
            val filteredLines = lines.filter { line ->
                var match = true
                if (!searchTerm.isNullOrEmpty()) match = match && line.contains(searchTerm, ignoreCase = true)
                if (!module.isNullOrEmpty()) match = match && line.contains("[$module]", ignoreCase = true)
                if (!level.isNullOrEmpty()) match = match && (line.contains("$level:", ignoreCase = true) || line.contains(" $level ", ignoreCase = true))
                match
            }.reversed()

            LogSearchResponseDTO(
                success = true,
                fileName = file,
                totalResults = filteredLines.size,
                results = filteredLines
            )
        } catch (e: Exception) {
            LogSearchResponseDTO(success = false, message = "Error al buscar: ${e.message}")
        }
    }

    @GetMapping("/db/errors")
    fun getDbErrors(
        @RequestParam(required = false) module: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) from: Long?,
        @RequestParam(required = false) to: Long?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "50") size: Int
    ): DbErrorsResponseDTO {
        return try {
            val fromDate = from?.let { Date(it) }
            val toDate = to?.let { Date(it) }
            val modulePart = if (module.isNullOrBlank()) null else module
            val searchPart = if (search.isNullOrBlank()) null else search

            val result = errorLogRepository.findWithFilters(
                module = modulePart,
                search = searchPart,
                from = fromDate,
                to = toDate,
                pageable = PageRequest.of(page, size)
            )

            DbErrorsResponseDTO(
                success = true,
                errors = result.content.map { it.toDTO() },
                total = result.totalElements,
                page = result.number,
                size = result.size,
                totalPages = result.totalPages
            )
        } catch (e: Exception) {
            DbErrorsResponseDTO(success = false, message = "Error al consultar DB: ${e.message}")
        }
    }

    @GetMapping("/db/stats")
    fun getDbStats(): DbStatsResponseDTO {
        return try {
            val now = Date()
            val startOfToday = Date.from(
                LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            )
            val oneHourAgo = Date(now.time - 3_600_000L)

            val total = errorLogRepository.countTotal()
            val today = errorLogRepository.countSince(startOfToday)
            val lastHour = errorLogRepository.countSince(oneHourAgo)
            val byModule = errorLogRepository.countByModule().map { row ->
                ModuleStatDTO(module = row[0].toString(), count = (row[1] as Long))
            }

            DbStatsResponseDTO(
                success = true,
                total = total,
                today = today,
                lastHour = lastHour,
                byModule = byModule
            )
        } catch (e: Exception) {
            DbStatsResponseDTO(success = false, message = "Error al obtener stats: ${e.message}")
        }
    }

    @GetMapping("/http-failures")
    fun getHttpFailures(
        @RequestParam(required = false, defaultValue = "all") statusRange: String,
        @RequestParam(required = false) uriSearch: String?,
        @RequestParam(required = false) from: Long?,
        @RequestParam(required = false) to: Long?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "50") size: Int
    ): HttpFailuresResponseDTO {
        return try {
            val logFile = findRequestLogFile()
                ?: return HttpFailuresResponseDTO(success = false, message = "No se encontró archivo de request log en $logDir")

            val ts = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
            val fromDt = from?.let { LocalDateTime.ofInstant(java.util.Date(it).toInstant(), ZoneId.systemDefault()) }
            val toDt = to?.let { LocalDateTime.ofInstant(java.util.Date(it).toInstant(), ZoneId.systemDefault()) }

            val failures = logFile.readLines()
                .filter { it.contains("[HTTP_FAILURE] ") }
                .mapNotNull { line ->
                    try {
                        val json = line.substringAfter("[HTTP_FAILURE] ")
                        val map: Map<String, Any> = objectMapper.readValue(json, object : TypeReference<Map<String, Any>>() {})
                        HttpFailureDTO(
                            ts = map["ts"]?.toString() ?: "",
                            id = map["id"]?.toString() ?: "",
                            method = map["method"]?.toString() ?: "",
                            uri = map["uri"]?.toString() ?: "",
                            query = map["query"]?.toString() ?: "",
                            status = (map["status"] as? Number)?.toInt() ?: 0,
                            ms = (map["ms"] as? Number)?.toLong() ?: 0,
                            ip = map["ip"]?.toString() ?: "",
                            contentType = map["contentType"]?.toString() ?: "",
                            userAgent = map["userAgent"]?.toString() ?: "",
                            authorization = map["authorization"]?.toString() ?: "",
                            reqBody = map["reqBody"]?.toString() ?: "",
                            resBody = map["resBody"]?.toString() ?: "",
                            error = map["error"]?.toString() ?: "",
                            stackTraceSummary = map["stackTraceSummary"]?.toString() ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                .filter { f ->
                    val matchesStatus = when (statusRange) {
                        "4xx" -> f.status in 400..499
                        "5xx" -> f.status >= 500
                        else -> true
                    }
                    val matchesUri = uriSearch.isNullOrBlank() || f.uri.contains(uriSearch, ignoreCase = true)
                    val matchesFrom = fromDt == null || runCatching { LocalDateTime.parse(f.ts, ts) >= fromDt }.getOrDefault(true)
                    val matchesTo = toDt == null || runCatching { LocalDateTime.parse(f.ts, ts) <= toDt }.getOrDefault(true)
                    matchesStatus && matchesUri && matchesFrom && matchesTo
                }
                .sortedByDescending { it.ts }

            val total = failures.size
            val totalPages = if (size > 0) (total + size - 1) / size else 1
            val paged = failures.drop(page * size).take(size)

            HttpFailuresResponseDTO(
                success = true,
                failures = paged,
                total = total,
                page = page,
                size = size,
                totalPages = totalPages
            )
        } catch (e: Exception) {
            HttpFailuresResponseDTO(success = false, message = "Error al leer http-failures: ${e.message}")
        }
    }

    private fun findRequestLogFile(): File? {
        val dir = File(logDir)
        if (!dir.exists() || !dir.isDirectory) return null
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".log") && it.name.contains("request", ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
            ?: dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".log") }
            ?.maxByOrNull { it.lastModified() }
    }

    @GetMapping(value = ["/viewer"], produces = [MediaType.TEXT_HTML_VALUE])
    fun logViewer(): String {
        return ClassPathResource("static/log-viewer.html").inputStream.readAllBytes()
            .toString(StandardCharsets.UTF_8)
    }

    private fun ErrorLog.toDTO() = ErrorLogDTO(
        id = id,
        module = module,
        error = error,
        data = data,
        date = date
    )
}
