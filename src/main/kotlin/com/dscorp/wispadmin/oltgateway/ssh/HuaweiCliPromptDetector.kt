package com.dscorp.wispadmin.oltgateway.ssh

internal object HuaweiCliPromptDetector {

    private val promptRegex = Regex("""(?m)[\w\-]+(?:\([^)\r\n]*\))?[>#]\s*$""")
    private val moreRegex = Regex("""----\s*More.*?----|More\s*\(.*?\)""", RegexOption.IGNORE_CASE)

    fun isComplete(buffer: String): Boolean {
        val cleaned = stripMore(buffer)
        if (moreRegex.containsMatchIn(buffer.takeLast(200))) {
            return false
        }
        return promptRegex.containsMatchIn(cleaned)
    }

    fun needsMorePage(buffer: String): Boolean {
        return moreRegex.containsMatchIn(buffer.takeLast(200))
    }

    fun stripMore(text: String): String {
        return text.replace(moreRegex, "")
    }
}
