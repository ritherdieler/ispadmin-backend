package com.dscorp.wispadmin.observability.dto

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.time.Instant

object FlexibleEpochMillis {
    fun fromText(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        text.toLongOrNull()?.let { return it }
        return runCatching { Instant.parse(text.trim()).toEpochMilli() }.getOrNull()
    }
}

class FlexibleEpochMillisDeserializer : JsonDeserializer<Long?>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): Long? {
        return when {
            parser.currentToken?.isNumeric == true -> parser.longValue
            parser.currentToken?.isScalarValue == true -> FlexibleEpochMillis.fromText(parser.text)
            else -> null
        }
    }
}
