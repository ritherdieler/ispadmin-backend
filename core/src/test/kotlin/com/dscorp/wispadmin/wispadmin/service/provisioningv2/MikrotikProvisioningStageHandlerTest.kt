package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.DeviceSessionRunner
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MikrotikProvisioningStageHandlerTest {
    private val repository = mockk<SubscriptionRepository>()
    private val session = mockk<MikrotikSession>(relaxed = true)
    private val runner: DeviceSessionRunner = { _, block -> block(session) }
    private val subscription = Subscription(id = 42, equipmentCondition = EquipmentCondition.LOAN).apply {
        fiberOnuSn = "HWTC9F4BF950"
        accessMode = AccessMode.PPPOE_DYNAMIC
        pppoeUsername = "gf42"
        pppoePasswordEnc = CrmSecretCipher("unit-test-key").encrypt("secret42")
        plan = Plan(id = 1, name = "F200", downloadSpeed = 200, uploadSpeed = 200)
        hostDevice = NetworkDevice(id = 8, name = "MK2")
    }
    private val operation = ProvisioningOperation("op", "staging", 42, "HWTC9F4BF950")

    @Test fun `captures absent baseline before creating and verifies an owned PPPoE secret`() {
        every { repository.lockIdentityOwner(42) } returns subscription
        every { session.print("/ppp/secret", mapOf("name" to "gf42"), any()) } returnsMany listOf(
            emptyList(), listOf(mapOf(".id" to "*1", "name" to "gf42", "profile" to "GF-200-200",
                "service" to "pppoe", "disabled" to "false", "comment" to "GFv2-staging-op")))
        val calls = mutableListOf<String>()
        val context = ProvisioningStageContext(operation, { calls += "lease" },
            { key, snapshot -> calls += "$key:$snapshot" }, { null })
        val handler = MikrotikProvisioningStageHandler(repository, CrmSecretCipher("unit-test-key"), runner)

        assertEquals(StageObservation.SATISFIED, handler.apply(context))

        assertEquals("lease", calls.first())
        assert(calls[1].startsWith("mikrotik:{\"secret\":null"))
        verify { session.add("/ppp/secret", match {
            it["name"] == "gf42" && it["profile"] == "GF-200-200" && it["password"] == "secret42" &&
                it["comment"] == "GFv2-staging-op" && it["service"] == "pppoe" && it["disabled"] == "false"
        }) }
    }

    @Test fun `refuses a foreign PPPoE secret without mutating MikroTik`() {
        every { repository.lockIdentityOwner(42) } returns subscription
        every { session.print("/ppp/secret", mapOf("name" to "gf42"), any()) } returns listOf(
            mapOf(".id" to "*1", "name" to "gf42", "comment" to "manual"))
        val context = ProvisioningStageContext(operation, {}, { _, _ -> }, { null })
        val handler = MikrotikProvisioningStageHandler(repository, CrmSecretCipher("unit-test-key"), runner)

        val error = assertThrows(ProvisioningStepException::class.java) { handler.apply(context) }

        assertEquals("MIKROTIK_SECRET_OWNERSHIP_CONFLICT", error.failure.code)
        verify(exactly = 0) { session.add(any(), any()) }
    }

    @Test fun `cancellation removes only the owned secret and its active sessions`() {
        every { repository.lockIdentityOwner(42) } returns subscription
        every { session.print("/ppp/secret", mapOf("name" to "gf42"), any()) } returnsMany listOf(
            listOf(mapOf(".id" to "*1", "name" to "gf42", "comment" to "GFv2-staging-op")), emptyList())
        every { session.print("/ppp/active", mapOf("name" to "gf42"), any()) } returns listOf(mapOf(".id" to "*active"))
        val context = ProvisioningStageContext(operation, {}, { _, _ -> }, { "{\"secret\":null}" })
        val handler = MikrotikProvisioningStageHandler(repository, CrmSecretCipher("unit-test-key"), runner)

        assertEquals(StageObservation.SATISFIED, handler.compensate(context))

        verifyOrder {
            session.remove("/ppp/active", "*active")
            session.remove("/ppp/secret", "*1")
        }
    }

    @Test fun `static registration accepts RouterOS normalized queue limits and reuses the owned queue`() {
        val static = subscription.apply {
            accessMode = AccessMode.STATIC_IP
            ip = "192.168.30.20"
            pppoeUsername = null
            pppoePasswordEnc = null
        }
        every { repository.lockIdentityOwner(42) } returns static
        val owned = mapOf(
            ".id" to "*queue", "target" to "192.168.30.20/32",
            "max-limit" to "200000000/200000000", "comment" to "GFv2-staging-op",
        )
        every { session.print("/queue/simple", mapOf("target" to "192.168.30.20/32"), any()) } returnsMany
            listOf(emptyList(), listOf(owned), listOf(owned))
        val captured = mutableMapOf<String, String>()
        val context = ProvisioningStageContext(operation, {}, { key, value -> captured[key] = value }, { captured[it] })
        val handler = MikrotikProvisioningStageHandler(repository, CrmSecretCipher("unit-test-key"), runner)

        assertEquals(StageObservation.SATISFIED, handler.apply(context))
        assertEquals(StageObservation.SATISFIED, handler.reconcile(context))
        assertTrue(captured.containsKey("mikrotik"))
        verify(exactly = 1) { session.add("/queue/simple", match {
            it["target"] == "192.168.30.20/32" && it["max-limit"] == "200M/200M" &&
                it["comment"] == "GFv2-staging-op"
        }) }
        verify(exactly = 0) { session.add("/ppp/secret", any()) }
    }

    @Test fun `static cancellation never removes a queue owned by another operation`() {
        subscription.apply { accessMode = AccessMode.STATIC_IP; ip = "192.168.30.20" }
        every { repository.lockIdentityOwner(42) } returns subscription
        every { session.print("/queue/simple", mapOf("target" to "192.168.30.20/32"), any()) } returns
            listOf(mapOf(".id" to "*foreign", "comment" to "GFv2-staging-other"))
        val context = ProvisioningStageContext(operation, {}, { _, _ -> }, { "{\"queue\":null}" })

        val error = assertThrows(ProvisioningStepException::class.java) {
            MikrotikProvisioningStageHandler(repository, CrmSecretCipher("unit-test-key"), runner).compensate(context)
        }

        assertEquals("MIKROTIK_QUEUE_OWNERSHIP_CONFLICT", error.failure.code)
        verify(exactly = 0) { session.remove(any(), any()) }
    }

    @Test fun `static cancellation removes its captured queue once`() {
        subscription.apply { accessMode = AccessMode.STATIC_IP; ip = "192.168.30.20" }
        every { repository.lockIdentityOwner(42) } returns subscription
        every { session.print("/queue/simple", mapOf("target" to "192.168.30.20/32"), any()) } returnsMany
            listOf(listOf(mapOf(".id" to "*owned", "comment" to "GFv2-staging-op")), emptyList())
        val context = ProvisioningStageContext(operation, {}, { _, _ -> }, { "{\"queue\":null}" })

        assertEquals(StageObservation.SATISFIED,
            MikrotikProvisioningStageHandler(repository, CrmSecretCipher("unit-test-key"), runner).compensate(context))

        verify(exactly = 1) { session.remove("/queue/simple", "*owned") }
        verify(exactly = 0) { session.remove("/ppp/secret", any()) }
    }

    @Test fun `static registration rejects a foreign queue with the assigned target`() {
        subscription.apply { accessMode = AccessMode.STATIC_IP; ip = "192.168.30.20" }
        every { repository.lockIdentityOwner(42) } returns subscription
        every { session.print("/queue/simple", mapOf("target" to "192.168.30.20/32"), any()) } returns
            listOf(mapOf(".id" to "*foreign", "comment" to "manual"))
        val context = ProvisioningStageContext(operation, {}, { _, _ -> }, { null })

        val error = assertThrows(ProvisioningStepException::class.java) {
            MikrotikProvisioningStageHandler(repository, CrmSecretCipher("unit-test-key"), runner).apply(context)
        }

        assertEquals("MIKROTIK_QUEUE_OWNERSHIP_CONFLICT", error.failure.code)
        verify(exactly = 0) { session.add(any(), any()) }
    }
}
