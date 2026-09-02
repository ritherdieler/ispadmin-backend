package com.dscorp.wispadmin.observability.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import javax.persistence.Table

class ObsSpanIndexesTest {

    private val declared = ObsSpan::class.java.getAnnotation(Table::class.java).indexes
        .associate { it.name to it.columnList.replace(" ", "") }

    @Test
    fun `obs_span declara solo los indices no redundantes`() {
        assertEquals(
            mapOf(
                "idx_obs_span_trace_parent" to "trace_id,parent_span_id",
                "idx_obs_span_root_start" to "parent_span_id,start_epoch_ms",
                "idx_obs_span_start" to "start_epoch_ms",
                "idx_obs_span_session" to "session_id"
            ),
            declared
        )
    }

    @Test
    fun `no quedan indices de una sola columna cubiertos por un compuesto`() {
        assertFalse(declared.containsKey("idx_obs_span_trace"))
        assertFalse(declared.containsKey("idx_obs_span_parent"))
    }

    @Test
    fun `ningun indice declarado incluye http_route porque se indexa por prefijo en la migracion`() {
        assertFalse(
            declared.values.any { it.contains("http_route") },
            declared.toString()
        )
    }
}
