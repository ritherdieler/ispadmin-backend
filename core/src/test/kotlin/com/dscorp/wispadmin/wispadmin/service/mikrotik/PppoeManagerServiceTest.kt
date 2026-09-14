package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PppoeManagerServiceTest {

    private val session = mockk<MikrotikSession>(relaxed = true)
    private lateinit var service: PppoeManagerService

    @BeforeEach
    fun setUp() {
        service = PppoeManagerService()
        every { session.print(any(), any(), any()) } returns emptyList()
        every { session.print("/ppp/profile", any(), any()) } returns listOf(
            mapOf(".id" to "*P", "name" to "GF-200-200", "rate-limit" to "200M/200M", "comment" to "Plan | UNKNOWN | 200/200 Mbps | S/ 50")
        )
    }

    @Test
    fun `ensureSecret adds the secret when it does not exist`() {
        val subscription = subscription(username = "gf2338", download = 200, upload = 200)
        val args = slot<Map<String, String>>()
        every { session.add("/ppp/secret", capture(args)) } returns Unit

        val result = service.ensureSecret(session, subscription, password = "s3cr3t0")

        assertTrue(result.created)
        assertFalse(result.updated)
        assertEquals("gf2338", args.captured["name"])
        assertEquals("s3cr3t0", args.captured["password"])
        assertEquals("GF-200-200", args.captured["profile"])
        assertEquals("pppoe", args.captured["service"])
        verify(exactly = 0) { session.set(any(), any(), any()) }
    }

    @Test
    fun `ensureSecret is idempotent and updates an existing secret instead of duplicating it`() {
        every { session.print("/ppp/secret", mapOf("name" to "gf2338"), any()) } returns listOf(
            mapOf(".id" to "*1A", "name" to "gf2338", "profile" to "GF-100-100", "service" to "pppoe")
        )
        val args = slot<Map<String, String>>()
        every { session.set("/ppp/secret", "*1A", capture(args)) } returns Unit

        val result = service.ensureSecret(
            session,
            subscription(username = "gf2338", download = 200, upload = 200),
            password = "s3cr3t0"
        )

        assertFalse(result.created)
        assertTrue(result.updated)
        assertEquals("GF-200-200", args.captured["profile"])
        verify(exactly = 0) { session.add(any(), any()) }
    }

    @Test
    fun `ensureSecret leaves an already aligned secret untouched`() {
        every { session.print("/ppp/secret", mapOf("name" to "gf2338"), any()) } returns listOf(
            mapOf(".id" to "*1A", "name" to "gf2338", "profile" to "GF-200-200", "service" to "pppoe", "disabled" to "false")
        )

        val result = service.ensureSecret(
            session,
            subscription(username = "gf2338", download = 200, upload = 200),
            password = null
        )

        assertFalse(result.created)
        assertFalse(result.updated)
        verify(exactly = 0) { session.add(any(), any()) }
        verify(exactly = 0) { session.set(any(), any(), any()) }
    }

    @Test
    fun `ensureSecret re enables a disabled secret`() {
        every { session.print("/ppp/secret", mapOf("name" to "gf2338"), any()) } returns listOf(
            mapOf(".id" to "*1A", "name" to "gf2338", "profile" to "GF-200-200", "service" to "pppoe", "disabled" to "true")
        )
        val args = slot<Map<String, String>>()
        every { session.set("/ppp/secret", "*1A", capture(args)) } returns Unit

        val result = service.ensureSecret(
            session,
            subscription(username = "gf2338", download = 200, upload = 200),
            password = null
        )

        assertTrue(result.updated)
        assertEquals("false", args.captured["disabled"])
    }

    @Test
    fun `ensureSecret refuses a subscription without a pppoe username`() {
        val result = service.ensureSecret(
            session,
            subscription(username = null, download = 200, upload = 200),
            password = "x"
        )

        assertFalse(result.created)
        assertFalse(result.updated)
        assertEquals("La suscripción no tiene username PPPoE", result.error)
        verify(exactly = 0) { session.add(any(), any()) }
    }

    @Test
    fun `ensureSecret refuses a plan without a profile in the catalog`() {
        val result = service.ensureSecret(
            session,
            subscription(username = "gf2338", download = 100, upload = 0),
            password = "x"
        )

        assertFalse(result.created)
        assertEquals("El plan no tiene velocidades válidas para derivar el perfil PPPoE", result.error)
        verify(exactly = 0) { session.add(any(), any()) }
    }

    @Test
    fun `ensureSecret never sends an empty password`() {
        val args = slot<Map<String, String>>()
        every { session.add("/ppp/secret", capture(args)) } returns Unit

        service.ensureSecret(session, subscription(username = "gf2338", download = 200, upload = 200), password = "   ")

        assertFalse(args.captured.containsKey("password"))
    }

    @Test
    fun `applyProfile switches the secret to the requested profile`() {
        every { session.print("/ppp/secret", mapOf("name" to "gf2338"), any()) } returns listOf(
            mapOf(".id" to "*1A", "name" to "gf2338", "profile" to "GF-200-200")
        )
        val args = slot<Map<String, String>>()
        every { session.set("/ppp/secret", "*1A", capture(args)) } returns Unit

        val applied = service.applyProfile(session, "gf2338", "GF-CORTE")

        assertTrue(applied)
        assertEquals("GF-CORTE", args.captured["profile"])
    }

    @Test
    fun `applyProfile does nothing when the secret is missing`() {
        val applied = service.applyProfile(session, "gf-inexistente", "GF-CORTE")

        assertFalse(applied)
        verify(exactly = 0) { session.set(any(), any(), any()) }
    }

    @Test
    fun `disableSecret disables the secret`() {
        every { session.print("/ppp/secret", mapOf("name" to "gf2338"), any()) } returns listOf(
            mapOf(".id" to "*1A", "name" to "gf2338")
        )
        val args = slot<Map<String, String>>()
        every { session.set("/ppp/secret", "*1A", capture(args)) } returns Unit

        assertTrue(service.disableSecret(session, "gf2338"))
        assertEquals("true", args.captured["disabled"])
    }

    @Test
    fun `kickSession removes the active session of the username`() {
        every { session.print("/ppp/active", mapOf("name" to "gf2338"), any()) } returns listOf(
            mapOf(".id" to "*9F", "name" to "gf2338", "address" to "10.64.0.15")
        )

        assertTrue(service.kickSession(session, "gf2338"))
        verify(exactly = 1) { session.remove("/ppp/active", "*9F") }
    }

    @Test
    fun `kickSession is harmless when the client is already offline`() {
        assertFalse(service.kickSession(session, "gf2338"))
        verify(exactly = 0) { session.remove(any(), any()) }
    }

    @Test
    fun `activeSessions reads every session in a single print`() {
        every { session.print("/ppp/active", emptyMap(), any()) } returns listOf(
            mapOf("name" to "gf2338", "address" to "10.64.0.15", "uptime" to "3h20m", "caller-id" to "AA:BB"),
            mapOf("name" to "gf2339", "address" to "10.64.0.16", "uptime" to "10m")
        )

        val sessions = service.activeSessions(session)

        assertEquals(2, sessions.size)
        assertEquals("gf2338", sessions[0].username)
        assertEquals("10.64.0.15", sessions[0].address)
        assertEquals("3h20m", sessions[0].uptime)
        assertEquals("AA:BB", sessions[0].callerId)
        assertNull(sessions[1].callerId)
        verify(exactly = 1) { session.print("/ppp/active", emptyMap(), any()) }
    }

    @Test
    fun `activeSessions skips rows without a username`() {
        every { session.print("/ppp/active", emptyMap(), any()) } returns listOf(
            mapOf("address" to "10.64.0.15"),
            mapOf("name" to "", "address" to "10.64.0.16"),
            mapOf("name" to "gf2338", "address" to "10.64.0.17")
        )

        val sessions = service.activeSessions(session)

        assertEquals(1, sessions.size)
        assertEquals("gf2338", sessions[0].username)
    }

    @Test
    fun `sessionOf finds a single client session`() {
        every { session.print("/ppp/active", mapOf("name" to "gf2338"), any()) } returns listOf(
            mapOf("name" to "gf2338", "address" to "10.64.0.15")
        )

        assertEquals("10.64.0.15", service.sessionOf(session, "gf2338")?.address)
        assertNull(service.sessionOf(session, "gf9999"))
    }

    @Test
    fun `ensureProfile creates a missing catalog profile with comment and rate`() {
        every { session.print("/ppp/profile", any(), any()) } returns emptyList()
        val args = slot<Map<String, String>>()
        every { session.add("/ppp/profile", capture(args)) } returns Unit

        val name = service.ensureProfile(
            session,
            Plan(id = 1, name = "FIBER 200", price = 80.0, downloadSpeed = 200, uploadSpeed = 200, type = InstallationType.FIBER),
        )

        assertEquals("GF-200-200", name)
        assertEquals("GF-200-200", args.captured["name"])
        assertEquals("200M/200M", args.captured["rate-limit"])
        assertEquals("PPPOE-DINAMICO", args.captured["remote-address"])
        assertEquals("10.64.0.1", args.captured["local-address"])
        assertEquals("FIBER 200 | FIBER | 200/200 Mbps | S/ 80", args.captured["comment"])
    }

    @Test
    fun `ensureSecret creates the profile when it is missing on the router`() {
        every { session.print("/ppp/profile", mapOf("name" to "GF-200-200"), any()) } returns emptyList()
        every { session.print("/ppp/secret", mapOf("name" to "gf2338"), any()) } returns emptyList()
        every { session.add("/ppp/profile", any()) } returns Unit
        every { session.add("/ppp/secret", any()) } returns Unit

        val result = service.ensureSecret(
            session,
            subscription(username = "gf2338", download = 200, upload = 200),
            password = "s3cr3t0"
        )

        assertTrue(result.created)
        verify { session.add("/ppp/profile", match { it["name"] == "GF-200-200" }) }
        verify { session.add("/ppp/secret", any()) }
    }

    @Test
    fun `ensureProfile is a no-op add when the profile already exists`() {
        every { session.print("/ppp/profile", mapOf("name" to "GF-200-200"), any()) } returns listOf(
            mapOf(
                ".id" to "*3",
                "name" to "GF-200-200",
                "comment" to "FIBER 200 | FIBER | 200/200 Mbps | S/ 80",
                "rate-limit" to "200M/200M",
            )
        )

        val name = service.ensureProfile(
            session,
            Plan(id = 1, name = "FIBER 200", price = 80.0, downloadSpeed = 200, uploadSpeed = 200, type = InstallationType.FIBER),
        )

        assertEquals("GF-200-200", name)
        verify(exactly = 0) { session.add("/ppp/profile", any()) }
    }

    private fun subscription(username: String?, download: Int, upload: Int) = Subscription(
        id = 2338,
        firstName = "Cliente",
        lastName = "PPPoE",
        dni = "00002338",
        equipmentCondition = EquipmentCondition.LOAN,
        serviceStatus = ServiceStatus.ACTIVE
    ).apply {
        this.accessMode = AccessMode.PPPOE_DYNAMIC
        this.pppoeUsername = username
        this.plan = Plan(
            id = 1,
            name = "Plan",
            price = 50.0,
            downloadSpeed = download,
            uploadSpeed = upload
        )
    }
}
