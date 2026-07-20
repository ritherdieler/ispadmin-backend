package com.dscorp.wispadmin.oltgateway.parser

import org.springframework.stereotype.Component

@Component
class OnuSummaryParser {

    private val snRowRegex = Regex(
        """(?m)^\s*(\d+)\s*/\s*(\d+)\s*/\s*(\d+)\s+(\d+)\s+(\S+)\s+(active|deactive)\s+(\S+)\s+(\S+)\s+(\S+)""",
        RegexOption.IGNORE_CASE
    )

    private val descriptionHeaderRegex = Regex(
        """(?im)^\s*F/S/P\s+ONT-ID\s+Description\s*$"""
    )

    private val descriptionRowRegex = Regex(
        """(?m)^\s*(\d+)\s*/\s*(\d+)\s*/\s*(\d+)\s+(\d+)\s+(.+?)\s*$"""
    )

    private val continuationRegex = Regex("""(?m)^\s{2,}(\S.*\S|\S)\s*$""")

    fun parse(output: String): List<ParsedOnuSummary> {
        val descriptions = parseDescriptions(output)
        return snRowRegex.findAll(output).map { match ->
            val frame = match.groupValues[1].toInt()
            val slot = match.groupValues[2].toInt()
            val port = match.groupValues[3].toInt()
            val ontId = match.groupValues[4].toInt()
            val key = descriptionKey(frame, slot, port, ontId)
            ParsedOnuSummary(
                frame = frame,
                slot = slot,
                port = port,
                ontId = ontId,
                sn = match.groupValues[5],
                controlFlag = match.groupValues[6].lowercase(),
                runState = match.groupValues[7],
                configState = match.groupValues[8],
                matchState = match.groupValues[9],
                description = descriptions[key]
            )
        }.toList()
    }

    private fun parseDescriptions(output: String): Map<String, String> {
        val header = descriptionHeaderRegex.find(output) ?: return emptyMap()
        val section = output.substring(header.range.last + 1)
        val result = linkedMapOf<String, StringBuilder>()
        var currentKey: String? = null

        for (line in section.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.matches(Regex("""(?i)MA5608T[>#].*"""))) {
                break
            }
            if (trimmed.isEmpty() || trimmed.all { it == '-' } || descriptionHeaderRegex.containsMatchIn(line)) {
                continue
            }
            val row = descriptionRowRegex.find(line)
            if (row != null) {
                val frame = row.groupValues[1].toInt()
                val slot = row.groupValues[2].toInt()
                val port = row.groupValues[3].toInt()
                val ontId = row.groupValues[4].toInt()
                currentKey = descriptionKey(frame, slot, port, ontId)
                result[currentKey!!] = StringBuilder(row.groupValues[5].trim())
                continue
            }
            val key = currentKey ?: continue
            val continuation = continuationRegex.find(line) ?: continue
            result[key]?.append(' ')?.append(continuation.groupValues[1].trim())
        }

        return result.mapValues { (_, value) -> value.toString().replace(Regex("""\s+"""), " ").trim() }
    }

    private fun descriptionKey(frame: Int, slot: Int, port: Int, ontId: Int): String {
        return "$frame/$slot/$port/$ontId"
    }
}
