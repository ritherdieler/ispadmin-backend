package com.dscorp.wispadmin.transport

import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

object InternalUris {
    fun uri(base: String, path: String, query: Map<String, Any?> = emptyMap()): URI {
        val builder = UriComponentsBuilder.fromHttpUrl(base.trim().trimEnd('/')).path(path)
        query.forEach { (key, value) ->
            if (value != null) {
                builder.queryParam(key, java.net.URLEncoder.encode(value.toString(), Charsets.UTF_8.name()).replace("+", "%20"))
            }
        }
        return builder.build(true).toUri()
    }

    fun path(base: String, vararg segments: String): URI {
        val builder = UriComponentsBuilder.fromHttpUrl(base.trim().trimEnd('/'))
        segments.forEach { builder.pathSegment(it) }
        return builder.build().encode().toUri()
    }
}
