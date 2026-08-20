package com.dscorp.wispadmin.wispadmin.service.genieacs

import java.net.URI

/**
 * Formats GenieACS NBI calls as copy-pasteable curl commands (Postman-compatible).
 */
object GenieAcsCurlLogger {

    fun formatPostTask(uri: URI, jsonBody: String): String =
        buildString {
            append("curl -X POST ").append(shellSingleQuoted(uri.toString()))
            append(" \\\n  -H ").append(shellSingleQuoted("Content-Type: application/json"))
            append(" \\\n  -d ").append(shellSingleQuoted(jsonBody))
        }

    fun formatGet(uri: URI): String =
        "curl -X GET ${shellSingleQuoted(uri.toString())}"

    internal fun shellSingleQuoted(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"
}
