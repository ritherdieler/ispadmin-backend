package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.observability.tracing.ObsTracer
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MikroTikServiceRestTest {

    private val session = mockk<MikrotikSession>(relaxed = true)
    private val obsTracer = mockk<ObsTracer>()
    private lateinit var service: MikroTikService

    @BeforeEach
    fun setUp() {
        every { obsTracer.span<Any>(any(), any(), any(), any()) } answers {
            arg<() -> Any>(3).invoke()
        }
        service = MikroTikService(obsTracer)
    }

    @Test
    fun `removeIpFromDebtorsList prints by list and address then removes each id`() {
        every {
            session.print("/ip/firewall/address-list", mapOf("list" to "deudores", "address" to "10.1.1.5"))
        } returns listOf(mapOf(".id" to "*7"))

        service.removeIpFromDebtorsList(session, "10.1.1.5")

        verify(exactly = 1) {
            session.print("/ip/firewall/address-list", mapOf("list" to "deudores", "address" to "10.1.1.5"))
        }
        verify(exactly = 1) { session.remove("/ip/firewall/address-list", "*7") }
    }

    @Test
    fun `addIpToDebtorsList uses add with list address and comment`() {
        service.addIpToDebtorsList(session, "10.1.1.8", "Cliente Test")

        verify(exactly = 1) {
            session.add(
                "/ip/firewall/address-list",
                mapOf(
                    "list" to "deudores",
                    "address" to "10.1.1.8",
                    "comment" to "Cliente Test"
                )
            )
        }
    }

    @Test
    fun `createFirewallDropRule adds forward drop on deudores list`() {
        every {
            session.print("/ip/firewall/filter", mapOf("comment" to "CORTADO POR DEUDA - LISTA DE DEUDORES"))
        } returns emptyList()

        service.createFirewallDropRule(session)

        verify(exactly = 1) {
            session.add(
                "/ip/firewall/filter",
                mapOf(
                    "chain" to "forward",
                    "action" to "drop",
                    "src-address-list" to "deudores",
                    "comment" to "CORTADO POR DEUDA - LISTA DE DEUDORES"
                )
            )
        }
    }

    @Test
    fun `findAndRemoveQueueByIp prints by target then removes last match`() {
        every {
            session.print("/queue/simple", mapOf("target" to "10.2.2.2/32"))
        } returns listOf(
            mapOf(".id" to "*1"),
            mapOf(".id" to "*2")
        )

        service.findAndRemoveQueueByIp(session, "10.2.2.2")

        verify(exactly = 1) { session.remove("/queue/simple", "*2") }
    }
}
