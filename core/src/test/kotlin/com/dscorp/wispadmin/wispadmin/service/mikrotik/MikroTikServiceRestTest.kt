package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.tracing.Tracer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MikroTikServiceRestTest {

    private val session = mockk<MikrotikSession>(relaxed = true)
    private val obsTracer = mockk<Tracer>()
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
        every { session.print("/ip/firewall/filter") } returns emptyList()

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
    fun `createFirewallDropRule places drop before blanket subscriber accept`() {
        every { session.print("/ip/firewall/filter") } returns listOf(
            mapOf(
                ".id" to "*11",
                "chain" to "forward",
                "action" to "accept",
                "in-interface-list" to "OLT-VLAN100",
                "comment" to "OLT sfp-sfpplus2 forward",
            )
        )
        every {
            session.print("/ip/firewall/filter", mapOf("comment" to "CORTADO POR DEUDA - LISTA DE DEUDORES"))
        } returns emptyList()

        service.createFirewallDropRule(session)

        verify(exactly = 1) {
            session.add(
                "/ip/firewall/filter",
                match { args ->
                    args["action"] == "drop" &&
                        args["src-address-list"] == "deudores" &&
                        args["place-before"] == "*11"
                }
            )
        }
    }

    @Test
    fun `addIpToCutList uses the list name of the given cut list`() {
        service.addIpToCutList(session, CutLists.CANCELLED, "10.1.1.9", "CANCELADO: Cliente Test")

        verify(exactly = 1) {
            session.add(
                "/ip/firewall/address-list",
                mapOf(
                    "list" to "cancelados",
                    "address" to "10.1.1.9",
                    "comment" to "CANCELADO: Cliente Test"
                )
            )
        }
    }

    @Test
    fun `removeIpFromCutList removes entries of the given list only`() {
        every {
            session.print("/ip/firewall/address-list", mapOf("list" to "cancelados", "address" to "10.1.1.9"))
        } returns listOf(mapOf(".id" to "*9"))

        service.removeIpFromCutList(session, CutLists.CANCELLED, "10.1.1.9")

        verify(exactly = 1) { session.remove("/ip/firewall/address-list", "*9") }
    }

    @Test
    fun `createCutDropRule for cancelled uses its own list and comment before subscriber accept`() {
        every { session.print("/ip/firewall/filter") } returns listOf(
            mapOf(
                ".id" to "*11",
                "chain" to "forward",
                "action" to "accept",
                "in-interface-list" to "OLT-VLAN100",
                "comment" to "OLT sfp-sfpplus2 forward",
            )
        )
        every {
            session.print("/ip/firewall/filter", mapOf("comment" to CutLists.CANCELLED.dropComment))
        } returns emptyList()

        service.createCutDropRule(session, CutLists.CANCELLED)

        verify(exactly = 1) {
            session.add(
                "/ip/firewall/filter",
                mapOf(
                    "chain" to "forward",
                    "action" to "drop",
                    "src-address-list" to "cancelados",
                    "comment" to CutLists.CANCELLED.dropComment,
                    "place-before" to "*11"
                )
            )
        }
    }

    @Test
    fun `createCutDropRule for cancelled does not touch the debtors rule`() {
        every { session.print("/ip/firewall/filter") } returns emptyList()
        every {
            session.print("/ip/firewall/filter", mapOf("comment" to CutLists.CANCELLED.dropComment))
        } returns listOf(mapOf(".id" to "*50"))

        service.createCutDropRule(session, CutLists.CANCELLED)

        verify(exactly = 1) { session.remove("/ip/firewall/filter", "*50") }
        verify(exactly = 0) {
            session.print("/ip/firewall/filter", mapOf("comment" to CutLists.DEBTORS.dropComment))
        }
    }

    @Test
    fun `removeIpFromAllCutLists clears the ip from debtors and cancelados`() {
        every {
            session.print("/ip/firewall/address-list", mapOf("list" to "deudores", "address" to "10.1.1.7"))
        } returns listOf(mapOf(".id" to "*1"))
        every {
            session.print("/ip/firewall/address-list", mapOf("list" to "cancelados", "address" to "10.1.1.7"))
        } returns listOf(mapOf(".id" to "*2"))

        service.removeIpFromAllCutLists(session, "10.1.1.7")

        verify(exactly = 1) { session.remove("/ip/firewall/address-list", "*1") }
        verify(exactly = 1) { session.remove("/ip/firewall/address-list", "*2") }
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
