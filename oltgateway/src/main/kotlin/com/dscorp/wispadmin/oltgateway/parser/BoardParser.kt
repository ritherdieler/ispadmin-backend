package com.dscorp.wispadmin.oltgateway.parser

import org.springframework.stereotype.Component

@Component
class BoardParser {

    private val tableRowRegex = Regex(
        """(?m)^[ \t]*(\d+)[ \t]+(\S+)[ \t]+(\S+)(?:[ \t]|$)"""
    )

    fun parseAll(output: String): List<ParsedBoard> {
        val fromTable = tableRowRegex.findAll(output).mapNotNull { match ->
            val name = match.groupValues[2]
            val status = match.groupValues[3]
            if (name.equals("BoardName", ignoreCase = true) || name.equals("SlotID", ignoreCase = true)) {
                return@mapNotNull null
            }
            if (name.all { it == '-' } || status.all { it == '-' }) {
                return@mapNotNull null
            }
            if (!name.any { it.isLetter() }) {
                return@mapNotNull null
            }
            ParsedBoard(
                slot = match.groupValues[1].toInt(),
                boardName = name,
                status = status
            )
        }.toList()
        if (fromTable.isNotEmpty()) {
            return fromTable.distinctBy { it.slot }
        }

        val boardName = fieldValue(output, "Board Name") ?: return emptyList()
        val status = fieldValue(output, "Board Status") ?: ""
        return listOf(ParsedBoard(slot = 0, boardName = boardName, status = status))
    }

    fun parse(output: String, expectedSlot: Int): ParsedBoard {
        val fromAll = parseAll(output).firstOrNull { it.slot == expectedSlot }
        if (fromAll != null) {
            return fromAll
        }
        val boardName = fieldValue(output, "Board Name") ?: ""
        val status = fieldValue(output, "Board Status") ?: ""
        return ParsedBoard(
            slot = expectedSlot,
            boardName = boardName,
            status = status
        )
    }

    private fun fieldValue(output: String, key: String): String? {
        val regex = Regex("""(?i)^\s*$key\s*:\s*(.+?)\s*$""", RegexOption.MULTILINE)
        return regex.find(output)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
