package com.dscorp.wispadmin.wispadmin.logging

object StackTraceSummarizer {

    fun summarize(
        throwable: Throwable,
        framesPerThrowable: Int = 12,
        maxCauses: Int = 3,
        maxTotalChars: Int = 2500
    ): String {
        val sb = StringBuilder()
        var current: Throwable? = throwable
        var causeIndex = 0
        while (current != null && causeIndex < maxCauses) {
            if (causeIndex > 0) sb.append("Caused by: ")
            sb.append(current.javaClass.name).append(": ").append(current.message ?: "").append('\n')
            val st = current.stackTrace
            val take = st.take(framesPerThrowable)
            take.forEach { el ->
                sb.append("  at ").append(el.className).append('.').append(el.methodName)
                    .append('(').append(el.fileName ?: "?").append(':').append(el.lineNumber).append(')')
                    .append('\n')
            }
            if (st.size > framesPerThrowable) {
                sb.append("  ... ").append(st.size - framesPerThrowable).append(" more\n")
            }
            current = current.cause
            causeIndex++
        }
        var out = sb.toString().trimEnd()
        if (out.length > maxTotalChars) {
            out = out.substring(0, maxTotalChars) + "\n...[truncated]"
        }
        return out
    }
}
