package com.dscorp.wispadmin.observability.service.symbolication

/**
 * Parses a ProGuard/R8 mapping.txt file and retraces obfuscated class/method names
 * (and line numbers, when line ranges are present) back to their original form.
 */
class ProguardMapping private constructor(
    private val classByObfuscated: Map<String, ClassMapping>
) {

    data class RetraceResult(
        val originalClass: String,
        val originalMethod: String?,
        val originalLine: Int?
    )

    private data class MethodMapping(
        val obfuscatedName: String,
        val originalName: String,
        val obfStartLine: Int,
        val obfEndLine: Int,
        val originalLine: Int
    )

    private class ClassMapping(
        val originalName: String,
        val methods: MutableList<MethodMapping> = ArrayList()
    )

    fun retrace(obfuscatedClass: String, obfuscatedMethod: String?, obfuscatedLine: Int?): RetraceResult? {
        val classMapping = classByObfuscated[obfuscatedClass] ?: return null
        if (obfuscatedMethod == null) {
            return RetraceResult(classMapping.originalName, null, null)
        }

        val candidates = classMapping.methods.filter { it.obfuscatedName == obfuscatedMethod }
        if (candidates.isEmpty()) {
            return RetraceResult(classMapping.originalName, null, null)
        }

        if (obfuscatedLine != null) {
            val byRange = candidates.firstOrNull {
                it.obfStartLine > 0 && obfuscatedLine in it.obfStartLine..it.obfEndLine
            }
            if (byRange != null) {
                val original = if (byRange.originalLine > 0) {
                    byRange.originalLine + (obfuscatedLine - byRange.obfStartLine)
                } else {
                    obfuscatedLine
                }
                return RetraceResult(classMapping.originalName, byRange.originalName, original)
            }
        }

        val distinctNames = candidates.map { it.originalName }.distinct()
        val method = if (distinctNames.size == 1) distinctNames.first() else candidates.first().originalName
        return RetraceResult(classMapping.originalName, method, obfuscatedLine)
    }

    companion object {
        private val CLASS_LINE = Regex("^([^\\s]+) -> ([^\\s:]+):$")
        private val METHOD_LINE = Regex("^\\s+(?:(\\d+):(\\d+):)?[^\\s]+\\s+([^\\s(]+)\\([^)]*\\)(?::(\\d+)(?::(\\d+))?)?\\s+->\\s+([^\\s]+)$")

        fun parse(content: String): ProguardMapping {
            val classes = HashMap<String, ClassMapping>()
            var current: ClassMapping? = null

            content.lineSequence().forEach { rawLine ->
                if (rawLine.isBlank() || rawLine.startsWith("#")) return@forEach
                val classMatch = CLASS_LINE.matchEntire(rawLine)
                if (classMatch != null) {
                    val original = classMatch.groupValues[1]
                    val obfuscated = classMatch.groupValues[2]
                    val mapping = ClassMapping(original)
                    classes[obfuscated] = mapping
                    current = mapping
                    return@forEach
                }

                if (rawLine[0] == ' ' || rawLine[0] == '\t') {
                    val active = current ?: return@forEach
                    val methodMatch = METHOD_LINE.matchEntire(rawLine) ?: return@forEach
                    val obfStart = methodMatch.groupValues[1].toIntOrNull() ?: 0
                    val obfEnd = methodMatch.groupValues[2].toIntOrNull() ?: obfStart
                    val originalName = methodMatch.groupValues[3]
                    val origStart = methodMatch.groupValues[4].toIntOrNull() ?: 0
                    val obfuscatedName = methodMatch.groupValues[6]
                    active.methods.add(
                        MethodMapping(obfuscatedName, originalName, obfStart, obfEnd, origStart)
                    )
                }
            }
            return ProguardMapping(classes)
        }
    }
}
