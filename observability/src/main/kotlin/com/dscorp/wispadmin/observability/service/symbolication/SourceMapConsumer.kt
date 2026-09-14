package com.dscorp.wispadmin.observability.service.symbolication

/**
 * Minimal Source Map v3 consumer: decodes the VLQ "mappings" string and resolves a
 * generated (line, column) position back to the original (source, line, column, name).
 */
class SourceMapConsumer(
    private val sources: List<String>,
    private val names: List<String>,
    mappings: String,
    private val sourceRoot: String?
) {

    data class OriginalPosition(
        val source: String?,
        val line: Int,
        val column: Int,
        val name: String?
    )

    private data class Segment(
        val generatedColumn: Int,
        val sourceIndex: Int,
        val originalLine: Int,
        val originalColumn: Int,
        val nameIndex: Int
    )

    // Indexed by generated line (0-based); each list is ordered by generatedColumn asc.
    private val lines: List<List<Segment>>

    init {
        lines = decode(mappings)
    }

    private fun decode(mappings: String): List<List<Segment>> {
        val result = ArrayList<List<Segment>>()
        var sourceIndex = 0
        var originalLine = 0
        var originalColumn = 0
        var nameIndex = 0

        for (lineStr in mappings.split(';')) {
            val segments = ArrayList<Segment>()
            var generatedColumn = 0
            if (lineStr.isNotEmpty()) {
                for (segStr in lineStr.split(',')) {
                    if (segStr.isEmpty()) continue
                    val fields = Vlq.decode(segStr)
                    if (fields.isEmpty()) continue
                    generatedColumn += fields[0]
                    if (fields.size >= 4) {
                        sourceIndex += fields[1]
                        originalLine += fields[2]
                        originalColumn += fields[3]
                        val resolvedName = if (fields.size >= 5) {
                            nameIndex += fields[4]
                            nameIndex
                        } else {
                            -1
                        }
                        segments.add(
                            Segment(generatedColumn, sourceIndex, originalLine, originalColumn, resolvedName)
                        )
                    } else {
                        segments.add(Segment(generatedColumn, -1, -1, -1, -1))
                    }
                }
            }
            segments.sortBy { it.generatedColumn }
            result.add(segments)
        }
        return result
    }

    /** line and column are 1-based (as they appear in stacktraces). */
    fun originalPositionFor(line: Int, column: Int): OriginalPosition? {
        val lineIdx = line - 1
        if (lineIdx < 0 || lineIdx >= lines.size) return null
        val segments = lines[lineIdx]
        if (segments.isEmpty()) return null

        val targetColumn = column - 1
        // Largest segment with generatedColumn <= targetColumn (fallback to first).
        var chosen = segments.first()
        for (seg in segments) {
            if (seg.generatedColumn <= targetColumn) chosen = seg else break
        }
        if (chosen.sourceIndex < 0) return null

        val source = sources.getOrNull(chosen.sourceIndex)?.let { prefixRoot(it) }
        val name = if (chosen.nameIndex >= 0) names.getOrNull(chosen.nameIndex) else null
        return OriginalPosition(
            source = source,
            line = chosen.originalLine + 1,
            column = chosen.originalColumn + 1,
            name = name
        )
    }

    private fun prefixRoot(source: String): String {
        val root = sourceRoot?.takeIf { it.isNotBlank() } ?: return source
        val normalized = if (root.endsWith("/")) root else "$root/"
        return normalized + source
    }
}

private object Vlq {
    private const val CONTINUATION = 0x20
    private const val MASK = 0x1F
    private val CHAR_TO_INT: IntArray = IntArray(128) { -1 }.also { table ->
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        alphabet.forEachIndexed { index, c -> table[c.code] = index }
    }

    fun decode(segment: String): IntArray {
        val values = ArrayList<Int>()
        var result = 0
        var shift = 0
        for (c in segment) {
            val code = if (c.code < CHAR_TO_INT.size) CHAR_TO_INT[c.code] else -1
            if (code < 0) return IntArray(0)
            val hasContinuation = (code and CONTINUATION) != 0
            val digit = code and MASK
            result += digit shl shift
            if (hasContinuation) {
                shift += 5
            } else {
                val negative = (result and 1) == 1
                var value = result shr 1
                if (negative) value = -value
                values.add(value)
                result = 0
                shift = 0
            }
        }
        return values.toIntArray()
    }
}
