package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthScope
import com.dscorp.wispadmin.servicehealth.controller.HealthActor
import com.dscorp.wispadmin.servicehealth.domain.HealthCursor
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.servicehealth.domain.WifiCurrent
import com.dscorp.wispadmin.servicehealth.port.HealthCpeCommand
import com.dscorp.wispadmin.servicehealth.port.HealthCpePort
import com.dscorp.wispadmin.servicehealth.repository.HealthCursorRepository
import com.dscorp.wispadmin.servicehealth.repository.RemoteActionRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiCurrentRepository
import com.dscorp.wispadmin.servicehealth.service.IdentityService
import com.dscorp.wispadmin.servicehealth.service.RemoteActionService
import com.dscorp.wispadmin.wispadmin.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.Optional

class RemoteActionWifiRefreshTest {
    private val actions = mockk<RemoteActionRepository>(relaxed = true)
    private val cursors = mockk<HealthCursorRepository>()
    private val wifi = mockk<WifiCurrentRepository>()
    private val subscriptions = mockk<SubscriptionRepository>()
    private val identity = mockk<IdentityService>()
    private val cpe = mockk<HealthCpePort>()
    private val txManager = mockk<PlatformTransactionManager>()
    private lateinit var remote: RemoteActionService
    private val actor = HealthActor(1, "TECHNICIAN")
    private lateinit var now: Instant

    @BeforeEach
    fun setup() {
        now = Instant.now()
        every { txManager.getTransaction(any()) } returns SimpleTransactionStatus()
        every { txManager.commit(any()) } returns Unit
        every { txManager.rollback(any()) } returns Unit
        every { cursors.lock("actions") } returns HealthCursor(cursorKey = "actions")
        every { cursors.findById(any()) } returns Optional.empty()
        every { actions.findByActorIdAndRequestKey(any(), any()) } returns null
        every { actions.findTopByDeviceKeyAndActionInOrderByCreatedAtDesc(any(), any()) } returns null
        every { actions.findByStatus("RUNNING") } returns emptyList()
        every { actions.saveAndFlush(any()) } answers {
            firstArg<com.dscorp.wispadmin.servicehealth.domain.RemoteAction>().also { if (it.id == null) it.id = 99L }
        }
        every { actions.save(any()) } answers { firstArg() }
        every { actions.findById(99L) } answers {
            Optional.of(com.dscorp.wispadmin.servicehealth.domain.RemoteAction(
                id = 99L, acsDeviceId = "sn1", subscriptionId = 1, actorId = 1, requestKey = "k",
                deviceKey = "ONU:SN1", action = "WIFI_REFRESH", status = "RUNNING", createdAt = now, requestDigest = "d",
            ))
        }
        every { subscriptions.findById(1) } returns Optional.of(
            Subscription(id = 1, fiberOnuSn = "sn1", equipmentCondition = EquipmentCondition.values().first()),
        )
        every { subscriptions.findAllIds() } returns listOf(1)
        every { identity.resolveOnu("sn1") } returns 1
        val properties = ServiceHealthProperties().apply {
            enabled = true
            actionsEnabled = true
            pilotSubscriptionIds = setOf(1)
            stationHmacKey = "k".repeat(32)
            periodicInformSeconds = 3600
        }
        val scope = ServiceHealthScope(properties, GigafiberEnvironmentProperties(), subscriptions)
        val cpeProvider = mockk<ObjectProvider<HealthCpePort>>()
        every { cpeProvider.ifAvailable } returns cpe
        remote = RemoteActionService(
            properties, scope, actions, cursors, subscriptions, wifi, identity, cpeProvider,
            TransactionTemplate(txManager), ObjectMapper(),
        )
    }

    @Test
    fun `manual wifi refresh accepts a sample younger than fifteen minutes`() {
        every { wifi.findById(1) } returns Optional.of(
            WifiCurrent(
                subscriptionId = 1,
                deviceId = "sn1",
                model = "V2804AX15T",
                informAt = now.minusSeconds(60),
                observedAt = now.minusSeconds(30),
                qualityStatus = Quality.FRESH,
            ),
        )
        every { cpe.wifiRefresh("sn1") } returns HealthCpeCommand(true, "PENDING")

        val result = remote.refresh(1, actor, "wifi-fresh")
        assertEquals("PENDING", result.status)
        verify(exactly = 1) { cpe.wifiRefresh("sn1") }
    }
}
