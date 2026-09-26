package com.dscorp.wispadmin.oltgateway.ssh

internal object HuaweiCliPromptDetector {

    private val promptRegex = Regex("""^[\w\-]+(?:\([^)\r\n]*\))?[>#]\s*$""")
    private val moreRegex = Regex("""----\s*More.*?----|More\s*\(.*?\)""", RegexOption.IGNORE_CASE)
    private val confirmCrTailRegex = Regex(
        """\{\s*<cr>[^}\r\n]*\}\s*:?\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun isComplete(buffer: String): Boolean {
        val cleaned = stripMore(buffer)
        if (needsMorePage(buffer)) {
            return false
        }
        if (needsConfirmEnter(buffer)) {
            return false
        }
        val lastLine = cleaned.lineSequence().map { it.trim('\r') }.lastOrNull { it.isNotBlank() } ?: return false
        return promptRegex.matches(lastLine.trim())
    }

    fun needsMorePage(buffer: String): Boolean {
        val tail = buffer.takeLast(240)
        val moreMatch = moreRegex.findAll(tail).lastOrNull() ?: return false
        val after = tail.substring(moreMatch.range.last + 1)
        return after.lineSequence().none { promptRegex.matches(it.trim().trim('\r')) }
    }

    fun needsConfirmEnter(buffer: String): Boolean {
        return confirmCrTailRegex.containsMatchIn(buffer.trimEnd())
    }

    fun stripMore(text: String): String {
        return text.replace(moreRegex, "")
    }
}
