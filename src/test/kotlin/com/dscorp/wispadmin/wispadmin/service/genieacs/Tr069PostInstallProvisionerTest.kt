package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus
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
    }

    private fun fiberSubscription(olt: OltProvisionStatus) = Subscription(
        id = 10,
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
