package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.dto.OnuDto
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.FiberInstallationStrategy
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class Tr069PostInstallProvisionerTest {

    private val properties = GenieAcsProperties().apply { enabled = true }
    private val provisioningService = mockk<Tr069ProvisioningService>()
    private val repository = mockk<SubscriptionRepository>()
    private val cipher = mockk<CrmSecretCipher>(relaxed = true)
    private val acsSyncService = mockk<SubscriptionAcsSyncService>(relaxed = true)
    private val fiberInstallationStrategy = mockk<FiberInstallationStrategy>()
    private val tagger = mockk<GenieAcsSubscriptionTagger>(relaxed = true)
    private lateinit var provisioner: Tr069PostInstallProvisioner

    @BeforeEach
    fun setUp() {
        provisioner = Tr069PostInstallProvisioner(
            properties = properties,
            provisioningService = provisioningService,
            repository = repository,
            cipher = cipher,
            acsSyncService = acsSyncService,
            fiberInstallationStrategy = fiberInstallationStrategy,
            tagger = tagger,
        )
        every { fiberInstallationStrategy.resolveVlan(any()) } returns "100"
        every { repository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `skips GenieACS when OLT not COMPLETE and keeps TR069 PENDING`() {
        val subscription = fiberSubscription(olt = OltProvisionStatus.PENDING)
        every { repository.findById(10) } returns Optional.of(subscription)

        val dto = provisioner.apply(
            SubscriptionDto(
                id = 10,
                installationType = InstallationType.FIBER,
                oltProvisionStatus = OltProvisionStatus.PENDING,
                tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
            ),
            fiberRequest(vlan = "100"),
        )

        verify(exactly = 0) { provisioningService.provision(any()) }
        assertEquals(Tr069ProvisionStatus.PENDING, dto.tr069ProvisionStatus)
        assertTrue(dto.tr069Message!!.contains("OLT", ignoreCase = true))
    }

    @Test
    fun `passes wanVlanId from resolveVlan when OLT COMPLETE`() {
        val subscription = fiberSubscription(olt = OltProvisionStatus.COMPLETE)
        every { repository.findById(10) } returns Optional.of(subscription)
        every { fiberInstallationStrategy.resolveVlan(any()) } returns "100"
        val requestSlot = slot<Tr069ProvisionRequest>()
        every { provisioningService.provision(capture(requestSlot)) } returns Tr069ProvisionOutcome(
            status = Tr069ProvisionStatus.COMPLETE,
            deviceId = "dev-1",
            message = "ok",
        )

        provisioner.apply(
            SubscriptionDto(
                id = 10,
                installationType = InstallationType.FIBER,
                oltProvisionStatus = OltProvisionStatus.COMPLETE,
                tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
            ),
            fiberRequest(vlan = "100"),
        )

        assertEquals(100, requestSlot.captured.wanVlanId)
        assertEquals("10 INTERNET JUAN PEREZ", requestSlot.captured.connectionName)
        assertFalse(requestSlot.captured.identityOnly)
        verify {
            tagger.apply(
                deviceId = "dev-1",
                subscriptionId = 10,
                kind = GenieAcsServiceKind.INTERNET,
                fullName = "Juan Perez",
                previousDeviceId = null,
            )
        }
    }

    @Test
    fun `FIBER duo plan uses DUO connection name`() {
        val subscription = fiberSubscription(olt = OltProvisionStatus.COMPLETE).apply {
            plan = Plan(id = 1, name = "Duo 200 Mbps", type = InstallationType.FIBER)
        }
        every { repository.findById(10) } returns Optional.of(subscription)
        val requestSlot = slot<Tr069ProvisionRequest>()
        every { provisioningService.provision(capture(requestSlot)) } returns Tr069ProvisionOutcome(
            status = Tr069ProvisionStatus.COMPLETE,
            deviceId = "dev-1",
            message = "ok",
        )

        provisioner.apply(
            SubscriptionDto(
                id = 10,
                installationType = InstallationType.FIBER,
                oltProvisionStatus = OltProvisionStatus.COMPLETE,
                tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
            ),
            fiberRequest(vlan = "100"),
        )

        assertEquals("10 DUO JUAN PEREZ", requestSlot.captured.connectionName)
        verify {
            tagger.apply(
                deviceId = "dev-1",
                subscriptionId = 10,
                kind = GenieAcsServiceKind.DUO,
                fullName = "Juan Perez",
                previousDeviceId = null,
            )
        }
    }

    @Test
    fun `ONLY_TV with ONU provisions identity Name only and tags`() {
        val subscription = fiberSubscription(olt = OltProvisionStatus.COMPLETE).apply {
            installationType = InstallationType.ONLY_TV_FIBER
            ip = null
            fiberOnu = Onu(sn = "VSOL0031C0B6", onu_type_name = "VSOLVA74")
            plan = Plan(id = 2, name = "TV Cable", type = InstallationType.ONLY_TV_FIBER)
        }
        every { repository.findById(10) } returns Optional.of(subscription)
        val requestSlot = slot<Tr069ProvisionRequest>()
        every { provisioningService.provision(capture(requestSlot)) } returns Tr069ProvisionOutcome(
            status = Tr069ProvisionStatus.COMPLETE,
            deviceId = "dev-tv",
            message = "ok",
        )

        provisioner.apply(
            SubscriptionDto(
                id = 10,
                installationType = InstallationType.ONLY_TV_FIBER,
                oltProvisionStatus = OltProvisionStatus.COMPLETE,
                tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
            ),
            fiberRequest(vlan = "100").copy(
                installationType = InstallationType.ONLY_TV_FIBER,
                wifiSsid24 = null,
                wifiSsid5 = null,
            ),
        )

        assertTrue(requestSlot.captured.identityOnly)
        assertEquals("10 TV JUAN PEREZ", requestSlot.captured.connectionName)
        verify {
            tagger.apply(
                deviceId = "dev-tv",
                subscriptionId = 10,
                kind = GenieAcsServiceKind.TV,
                fullName = "Juan Perez",
                previousDeviceId = null,
            )
        }
    }

    @Test
    fun `ONLY_TV CATV without ONU skips GenieACS`() {
        val subscription = fiberSubscription(olt = OltProvisionStatus.NA).apply {
            installationType = InstallationType.ONLY_TV_FIBER
            fiberOnu = null
            oltProvisionStatus = OltProvisionStatus.NA
            tr069ProvisionStatus = Tr069ProvisionStatus.NA
        }
        every { repository.findById(10) } returns Optional.of(subscription)

        val dto = provisioner.apply(
            SubscriptionDto(
                id = 10,
                installationType = InstallationType.ONLY_TV_FIBER,
                oltProvisionStatus = OltProvisionStatus.NA,
                tr069ProvisionStatus = Tr069ProvisionStatus.NA,
            ),
            fiberRequest(vlan = "100").copy(
                installationType = InstallationType.ONLY_TV_FIBER,
                onu = null,
            ),
        )

        verify(exactly = 0) { provisioningService.provision(any()) }
        verify(exactly = 0) { tagger.apply(any(), any(), any(), any(), any()) }
        assertEquals(Tr069ProvisionStatus.NA, dto.tr069ProvisionStatus)
    }

    @Test
    fun `tags apply on MANUAL_REQUIRED when deviceId is present and previous device is cleared`() {
        val subscription = fiberSubscription(olt = OltProvisionStatus.COMPLETE).apply {
            tr069DeviceId = "old-dev"
        }
        every { repository.findById(10) } returns Optional.of(subscription)
        every { provisioningService.provision(any()) } returns Tr069ProvisionOutcome(
            status = Tr069ProvisionStatus.MANUAL_REQUIRED,
            deviceId = "dev-1",
            error = "SPV 9008",
        )

        provisioner.apply(
            SubscriptionDto(
                id = 10,
                installationType = InstallationType.FIBER,
                oltProvisionStatus = OltProvisionStatus.COMPLETE,
                tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
            ),
            fiberRequest(vlan = "100"),
        )

        verify {
            tagger.apply(
                deviceId = "dev-1",
                subscriptionId = 10,
                kind = GenieAcsServiceKind.INTERNET,
                fullName = "Juan Perez",
                previousDeviceId = "old-dev",
            )
        }
    }

    private fun fiberSubscription(olt: OltProvisionStatus) = Subscription(
        id = 10,
        firstName = "Juan",
        lastName = "Perez",
        installationType = InstallationType.FIBER,
        ip = "192.168.30.10",
        vlan = "100",
        wifiSsid24 = "acs2g",
        wifiSsid5 = "acs5g",
        oltProvisionStatus = olt,
        tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
        equipmentCondition = EquipmentCondition.LOAN,
    )

    private fun fiberRequest(vlan: String) = SubscriptionRequest(
        firstName = "Juan",
        lastName = "Perez",
        dni = "12345678",
        address = "Calle 1",
        phone = "999888777",
        subscriptionDate = System.currentTimeMillis(),
        planId = 1,
        additionalDeviceIds = emptyList(),
        placeId = 1,
        location = GeoLocation(-11.0, -77.0),
        technicianId = 1,
        hostDeviceId = 1,
        installationType = InstallationType.FIBER,
        vlan = vlan,
        wifiSsid24 = "acs2g",
        wifiSsid5 = "acs5g",
        onu = OnuDto(sn = "VSOL0031C0B6", onu_type_name = "VSOLVA74"),
    )
}
