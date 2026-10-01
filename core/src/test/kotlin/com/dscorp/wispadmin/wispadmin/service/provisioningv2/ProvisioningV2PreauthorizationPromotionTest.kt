package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.OnuDto
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmSecretCipher
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID

class ProvisioningV2PreauthorizationPromotionTest {
    private val dataSource = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
    private val jdbc = JdbcTemplate(dataSource)
    private val json = jacksonObjectMapper().findAndRegisterModules()
    private val journal = ProvisioningJournal(jdbc, json)
    private val resources = ProvisioningResourceStore(jdbc, CrmSecretCipher("unit-test-only"))
    private val properties = ProvisioningV2Properties().apply { enabled = true }
    private val environment = GigafiberEnvironmentProperties().apply { tag = "lab" }
    private val service = ProvisioningV2RegistrationService(journal, resources, json, environment, properties)

    init { ProvisioningTestSchema.initialize(dataSource) }

    @Test
    fun `new fiber registration requires ONU preauthorization`() {
        val serial = "VSOL0031C0B6"
        val request = SubscriptionRequest(
            firstName = "Cliente", lastName = "Prueba", dni = "dni", address = "Calle", phone = "900000000",
            subscriptionDate = 1_790_640_000_000, planId = 1, additionalDeviceIds = emptyList(), placeId = 1,
            location = GeoLocation(-12.0, -77.0), technicianId = 71, hostDeviceId = 1,
            onu = OnuDto(olt_id = "olt-1", pon_type = "GPON", board = "0", port = "1", onu_type_name = "VSOLVA74", sn = serial),
            installationType = InstallationType.FIBER, vlan = "100",
        )
        val subscription = Subscription(id = 83, installationType = InstallationType.FIBER, fiberOnuSn = serial,
            equipmentCondition = EquipmentCondition.LOAN)

        val error = assertThrows(IllegalStateException::class.java) {
            service.start(subscription, request, 71)
        }

        assertEquals("PREAUTHORIZATION_REQUIRED", error.message)
    }

    @Test
    fun `preauthorization promotion seeds OLT and ACS as completed without reauthorizing`() {
        val target = ProvisioningOnuTarget("olt-1", "GPON", "0", "1", "VSOLVA74", 100)
        val pending = ProvisioningOperation(
            id = "preauthorization-1", environment = "lab", subscriptionId = null, serial = "VSOL0031C0B6",
            flowVersion = 3,
            phase = ProvisioningPhase.OLT_AUTHORIZATION, operatorId = 71,
            registrationRequestKey = "client-request-1", onuTarget = target,
        )
        journal.insertPreauthorization(pending)
        journal.updatePreauthorization("lab", pending.id, pending.revision, transform = { current -> current.copy(
            phase = ProvisioningPhase.READY_FOR_FORM,
            state = ProvisioningState.READY_FOR_FORM,
            oltEvidence = OltProvisioningResource("olt-external-1", 0, 1, 7),
            acsContactEvidence = AcsContactProvisioningResource("cpe-1", "VSOLVA74", "1.0"),
            checkpoints = current.checkpoints.map { checkpoint ->
                if (checkpoint.stage in setOf(ProvisioningStage.OLT, ProvisioningStage.ACS_CONTACT))
                    checkpoint.copy(state = CheckpointState.SUCCEEDED, touched = checkpoint.stage == ProvisioningStage.OLT)
                else checkpoint
            },
        ) })
        val form = SubscriptionRequest(
            firstName = "Cliente", lastName = "Prueba", dni = "dni", address = "Calle", phone = "900000000",
            subscriptionDate = 1_790_640_000_000, planId = 1, additionalDeviceIds = emptyList(), placeId = 1,
            location = GeoLocation(-12.0, -77.0), technicianId = 71, hostDeviceId = 1,
            onu = OnuDto(olt_id = "olt-1", pon_type = "GPON", board = "0", port = "1", onu_type_name = "VSOLVA74", sn = pending.serial),
            installationType = InstallationType.FIBER, vlan = "100", registrationOperationId = pending.id,
            wifiPassword24 = "wifi-secret",
        )
        val subscription = Subscription(id = 82, installationType = InstallationType.FIBER, fiberOnuSn = pending.serial,
            equipmentCondition = EquipmentCondition.LOAN)

        assertThrows(IllegalArgumentException::class.java) {
            service.start(subscription.copy(fiberOnuSn = "VSOL9999C0B6"), form, 71)
        }

        val promoted = service.start(subscription, form, 71)

        assertEquals(ProvisioningPhase.PROVISIONING, promoted.phase)
        assertEquals(71, promoted.operatorId)
        assertTrue(promoted.checkpoints.filter { it.stage in setOf(
            ProvisioningStage.OLT, ProvisioningStage.ACS_CONTACT,
        ) }.all { it.state == CheckpointState.SUCCEEDED })
        assertEquals(ProvisioningStage.VALIDATE, ProvisioningTransitions().next(promoted))
        val saved = requireNotNull(resources.snapshot("lab", pending.id, ProvisioningV2RegistrationService.REGISTRATION_RESOURCE_KEY))
        assertFalse(jdbc.queryForObject("SELECT snapshot_cipher FROM provisioning_v2_resource WHERE operation_id=? AND resource_key='registration'",
            String::class.java, pending.id)!!.contains("wifi-secret"))
        assertTrue(saved.contains("wifi-secret"))
        assertEquals("olt-external-1", json.readValue(resources.snapshot("lab", pending.id, "olt"), OltProvisioningResource::class.java).externalId)
        assertNull(resources.snapshot("lab", pending.id, "legacy-management"))
    }
}
