package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.service.subscription.strategies.FiberInstallationStrategy
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.dto.OnuDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class Tr069PostInstallProvisionerAcsSyncTest {

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
        every { fiberInstallationStrategy.resolveVlan(any()) } returns "1"
        every { repository.findById(10) } returns Optional.of(
            Subscription(
                id = 10,
                installationType = InstallationType.FIBER,
                ip = "192.168.123.4",
                vlan = "1",
                wifiSsid24 = "acs2g",
                wifiSsid5 = "acs5g",
                oltProvisionStatus = OltProvisionStatus.COMPLETE,
                tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
                equipmentCondition = EquipmentCondition.LOAN,
            )
        )
        every { repository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `COMPLETE outcome triggers acs upsert`() {
        val outcome = Tr069ProvisionOutcome(
            status = Tr069ProvisionStatus.COMPLETE,
            deviceId = "dev-1",
            message = "ok",
            acsSnapshot = Tr069AcsSnapshot(serialSuffix = "31C0B6", ssid24 = "acs2g"),
        )
        every { provisioningService.provision(any()) } returns outcome

        provisioner.apply(
            SubscriptionDto(
                id = 10,
                installationType = InstallationType.FIBER,
                oltProvisionStatus = OltProvisionStatus.COMPLETE,
            ),
            fiberRequest(),
        )

        verify(exactly = 1) {
            acsSyncService.upsertFromProvision(
                subscriptionId = 10,
                outcome = outcome,
                smartoltSerial = "VSOL0031C0B6",
            )
        }
    }

    @Test
    fun `MANUAL without device still invokes sync service which may no-op`() {
        val outcome = Tr069ProvisionOutcome(
            status = Tr069ProvisionStatus.MANUAL_REQUIRED,
            deviceId = null,
            error = "timeout",
        )
        every { provisioningService.provision(any()) } returns outcome

        provisioner.apply(
            SubscriptionDto(
                id = 10,
                installationType = InstallationType.FIBER,
                oltProvisionStatus = OltProvisionStatus.COMPLETE,
            ),
            fiberRequest(),
        )

        verify(exactly = 1) {
            acsSyncService.upsertFromProvision(
                subscriptionId = 10,
                outcome = outcome,
                smartoltSerial = "VSOL0031C0B6",
            )
        }
    }

    private fun fiberRequest() = SubscriptionRequest(
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
        vlan = "1",
        wifiSsid24 = "acs2g",
        wifiSsid5 = "acs5g",
        onu = OnuDto(sn = "VSOL0031C0B6", onu_type_name = "V2804AX15T"),
    )
}
