package com.dscorp.wispadmin.observability.config

import com.dscorp.wispadmin.observability.entity.ObsSpan
import com.dscorp.wispadmin.observability.service.ObsSpanCollector
import com.dscorp.wispadmin.observability.tracing.TraceContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletResponse

class TraceContextFilterSamplingTest {

    private val collector = mockk<ObsSpanCollector>(relaxed = true)
    private val properties = ObservabilityProperties()
    private lateinit var filter: TraceContextFilter
    private var now = 1_000_000L
    private var dice = 0.5

    @BeforeEach
    fun setup() {
        properties.enabled = true
        properties.tracing.enabled = true
        properties.tracing.sampleRate = 1.0
        properties.tracing.alwaysSampleAboveMs = 1_000
        filter = TraceContextFilter(collector, properties).apply {
            clock = { now }
            randomSupplier = { dice }
        }
    }

    private fun run(
        status: Int = 200,
        elapsedMs: Long = 10,
        traceParent: String? = null
    ): MockHttpServletResponse {
        val request = MockHttpServletRequest("GET", "/subscription/all")
        traceParent?.let { request.addHeader(TraceContextFilter.HEADER_TRACEPARENT, it) }
        val response = MockHttpServletResponse()
        val chain = FilterChain { _: ServletRequest, res: ServletResponse ->
            now += elapsedMs
            (res as HttpServletResponse).status = status
        }
        filter.doFilter(request, response, chain)
        return response
    }

    @Test
    fun `con sample rate 1 conserva todos los spans SERVER`() {
        run()

        verify(exactly = 1) { collector.enqueue(any()) }
    }

    @Test
    fun `fuera de la muestra descarta el span SERVER rapido y correcto`() {
        properties.tracing.sampleRate = 0.1
        dice = 0.9

        run(status = 200, elapsedMs = 10)

        verify(exactly = 0) { collector.enqueue(any()) }
    }

    @Test
    fun `fuera de la muestra conserva el span SERVER con error`() {
        properties.tracing.sampleRate = 0.1
        dice = 0.9

        val captured = slot<ObsSpan>()
        every { collector.enqueue(capture(captured)) } returns Unit

        run(status = 500, elapsedMs = 10)

        assertEquals("ERROR", captured.captured.status)
    }

    @Test
    fun `fuera de la muestra conserva el span SERVER lento`() {
        properties.tracing.sampleRate = 0.1
        dice = 0.9

        val captured = slot<ObsSpan>()
        every { collector.enqueue(capture(captured)) } returns Unit

        run(status = 200, elapsedMs = 1_500)

        assertEquals(1_500L, captured.captured.durationMs)
    }

    @Test
    fun `dentro de la muestra conserva aunque sea rapido y correcto`() {
        properties.tracing.sampleRate = 0.5
        dice = 0.1

        run(status = 200, elapsedMs = 10)

        verify(exactly = 1) { collector.enqueue(any()) }
    }

    @Test
    fun `respeta la decision de no muestrear que llega en traceparent`() {
        properties.tracing.sampleRate = 1.0

        run(
            status = 200,
            elapsedMs = 10,
            traceParent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-00"
        )

        verify(exactly = 0) { collector.enqueue(any()) }
    }

    @Test
    fun `publica el flag de muestreo en el traceparent de respuesta`() {
        properties.tracing.sampleRate = 0.0

        val response = run()

        val header = response.getHeader(TraceContextFilter.HEADER_TRACEPARENT)!!
        assertTrue(header.endsWith("-00"), header)
    }

    @Test
    fun `expone la decision de muestreo en el scope para los spans hijos`() {
        properties.tracing.sampleRate = 0.0
        var sampledInsideRequest: Boolean? = null
        val request = MockHttpServletRequest("GET", "/subscription/all")
        val chain = FilterChain { _: ServletRequest, _: ServletResponse ->
            sampledInsideRequest = TraceContext.current()?.sampled
        }

        filter.doFilter(request, MockHttpServletResponse(), chain)

        assertFalse(sampledInsideRequest!!)
    }

    @Test
    fun `no traza las rutas de observabilidad`() {
        val request = MockHttpServletRequest("POST", "/observability/events")
        val chain = MockFilterChain()

        filter.doFilter(request, MockHttpServletResponse(), chain)

        verify(exactly = 0) { collector.enqueue(any()) }
    }
}
