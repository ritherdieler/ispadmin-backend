package com.dscorp.wispadmin.transport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import javax.servlet.FilterChain

class RegistrationTimingTest {

    @Test
    fun `enabled in staging profile even when prod is also active`() {
        val env = MockEnvironment()
        env.setActiveProfiles("prod", "staging", "subsystem")
        env.setProperty("gigafiber.registration.timing.enabled", "false")
        env.setProperty("spring.datasource.url", "jdbc:mysql://mysql:3306/ispadmin_staging")
        assertTrue(RegistrationTimingSupport.enabled(env))
    }

    @Test
    fun `enabled for staging ACS war via context path`() {
        val env = MockEnvironment()
        env.setActiveProfiles("prod", "acs")
        env.setProperty("gigafiber.registration.timing.enabled", "false")
        env.setProperty("server.servlet.context-path", "/ispadmin-staging-acs")
        env.setProperty("spring.datasource.url", "jdbc:mysql://mysql:3306/stg_acs")
        assertTrue(RegistrationTimingSupport.enabled(env))
    }

    @Test
    fun `enabled for local gateway via localhost jdbc`() {
        val env = MockEnvironment()
        env.setActiveProfiles("prod", "oltgateway")
        env.setProperty("spring.datasource.url", "jdbc:mysql://localhost:3306/ispadmin_dev")
        assertTrue(RegistrationTimingSupport.enabled(env))
    }

    @Test
    fun `disabled on production core`() {
        val env = MockEnvironment()
        env.setActiveProfiles("prod")
        env.setProperty("gigafiber.registration.timing.enabled", "false")
        env.setProperty("server.servlet.context-path", "/ispadmin")
        env.setProperty("spring.datasource.url", "jdbc:mysql://mysql:3306/ispadmin?useUnicode=yes")
        assertFalse(RegistrationTimingSupport.enabled(env))
    }

    @Test
    fun `disabled on production acs`() {
        val env = MockEnvironment()
        env.setActiveProfiles("prod", "acs")
        env.setProperty("server.servlet.context-path", "/ispadmin-acs")
        env.setProperty("spring.datasource.url", "jdbc:mysql://mysql:3306/prod_acs?useUnicode=yes")
        assertFalse(RegistrationTimingSupport.enabled(env))
    }

    @Test
    fun `enabled in dev profile`() {
        val env = MockEnvironment()
        env.setActiveProfiles("dev", "local")
        assertTrue(RegistrationTimingSupport.enabled(env))
    }

    @Test
    fun `enabled in local-prestaging profile even when prod is also active`() {
        val env = MockEnvironment()
        env.setActiveProfiles("prod", "oltgateway", "local-prestaging")
        env.setProperty("gigafiber.registration.timing.enabled", "false")
        env.setProperty("server.servlet.context-path", "/ispadmin")
        env.setProperty("spring.datasource.url", "jdbc:mysql://mysql:3306/prod_oltgateway?useUnicode=yes")
        assertTrue(RegistrationTimingSupport.enabled(env))
    }

    @Test
    fun `enabled when environment tag is lpstg`() {
        val env = MockEnvironment()
        env.setActiveProfiles("prod")
        env.setProperty("gigafiber.registration.timing.enabled", "false")
        env.setProperty("gigafiber.environment.tag", "lpstg")
        env.setProperty("server.servlet.context-path", "/ispadmin")
        env.setProperty("spring.datasource.url", "jdbc:mysql://mysql:3306/ispadmin?useUnicode=yes")
        assertTrue(RegistrationTimingSupport.enabled(env))
    }

    @Test
    fun `enabled for prestaging jdbc schema`() {
        val env = MockEnvironment()
        env.setActiveProfiles("prod", "acs")
        env.setProperty("gigafiber.registration.timing.enabled", "false")
        env.setProperty("server.servlet.context-path", "/ispadmin-acs")
        env.setProperty("spring.datasource.url", "jdbc:mysql://dbhost:3306/prestaging_acs?useUnicode=yes")
        assertTrue(RegistrationTimingSupport.enabled(env))
    }

    @Test
    fun `formats span line for grep`() {
        val lines = mutableListOf<String>()
        val timing = RegistrationTiming(
            enabled = true,
            war = "core",
            sink = { lines += it },
            clock = sequence {
                yield(1000L)
                yield(1450L)
            }.iterator().let { { it.next() } },
        )
        val result = timing.span("core.gateway.activate", mapOf("sn" to "ZTEGDC47BFFD")) { "ok" }
        assertEquals("ok", result)
        assertEquals(1, lines.size)
        assertTrue(lines[0].startsWith("REG_TIMING "))
        assertTrue(lines[0].contains("war=core"))
        assertTrue(lines[0].contains("kind=span"))
        assertTrue(lines[0].contains("name=core.gateway.activate"))
        assertTrue(lines[0].contains("ms=450"))
        assertTrue(lines[0].contains("sn=ZTEGDC47BFFD"))
    }

    @Test
    fun `noop when disabled`() {
        val lines = mutableListOf<String>()
        val timing = RegistrationTiming(enabled = false, war = "core", sink = { lines += it })
        timing.span("core.mk.pppoe") { 1 }
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `matches registration paths across wars`() {
        assertTrue(RegistrationTimingSupport.isRegistrationPath("/ispadmin/subscription"))
        assertTrue(RegistrationTimingSupport.isRegistrationPath("/ispadmin/subscription/2402/registration-progress"))
        assertTrue(RegistrationTimingSupport.isRegistrationPath("/ispadmin/api/olt-gateway/onu/activate"))
        assertTrue(RegistrationTimingSupport.isRegistrationPath("/ispadmin-staging-acs/api/acs/v1/cpe/provision"))
        assertTrue(RegistrationTimingSupport.isRegistrationPath("/ispadmin/onu/unconfigured_onus"))
        assertFalse(RegistrationTimingSupport.isRegistrationPath("/ispadmin/actuator/health"))
        assertFalse(RegistrationTimingSupport.isRegistrationPath("/ispadmin-staging-acs/api/acs/v1/health"))
    }

    @Test
    fun `filter logs incoming http duration when enabled`() {
        val lines = mutableListOf<String>()
        val timing = RegistrationTiming(enabled = true, war = "gateway", sink = { lines += it })
        val filter = RegistrationTimingFilter(timing)
        val request = MockHttpServletRequest("POST", "/api/olt-gateway/onu/activate")
        request.contextPath = "/ispadmin-staging-oltgateway"
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, FilterChain { _, res ->
            Thread.sleep(5)
            (res as MockHttpServletResponse).status = 200
        })
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("kind=http.in"))
        assertTrue(lines[0].contains("method=POST"))
        assertTrue(lines[0].contains("path=/api/olt-gateway/onu/activate"))
        assertTrue(lines[0].contains("status=200"))
        assertTrue(lines[0].contains("ms="))
        assertTrue(lines[0].contains("war=gateway"))
    }

    @Test
    fun `war name from context path`() {
        assertEquals("acs", RegistrationTimingSupport.warName("/ispadmin-staging-acs"))
        assertEquals("gateway", RegistrationTimingSupport.warName("/ispadmin-staging-oltgateway"))
        assertEquals("core", RegistrationTimingSupport.warName("/ispadmin"))
        assertEquals("core", RegistrationTimingSupport.warName("/ispadmin-staging"))
    }
}
