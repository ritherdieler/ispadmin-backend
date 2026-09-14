package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.data.model.MikrotikProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.dto.RegistrationStep
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegistrationProgressMapperTest {

    @Test
    fun `OLT pending maps to AUTHORIZING_ONU`() {
        val progress = RegistrationProgressMapper.from(
            SubscriptionDto(
                id = 1,
                oltProvisionStatus = OltProvisionStatus.PENDING,
                mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING,
                tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
            )
        )
        assertEquals(RegistrationStep.AUTHORIZING_ONU, progress.step)
        assertFalse(progress.done)
        assertEquals("Autorizando ONU…", progress.message)
    }

    @Test
    fun `OLT complete and mikrotik pending maps to PROVISIONING_MIKROTIK`() {
        val progress = RegistrationProgressMapper.from(
            SubscriptionDto(
                id = 1,
                oltProvisionStatus = OltProvisionStatus.COMPLETE,
                mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING,
                tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
            )
        )
        assertEquals(RegistrationStep.PROVISIONING_MIKROTIK, progress.step)
        assertFalse(progress.done)
    }

    @Test
    fun `TR-069 pending waiting ACS maps to WAITING_ACS`() {
        val progress = RegistrationProgressMapper.from(
            SubscriptionDto(
                id = 1,
                oltProvisionStatus = OltProvisionStatus.COMPLETE,
                mikrotikProvisionStatus = MikrotikProvisionStatus.COMPLETE,
                tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
                tr069Message = "Buscando CPE en GenieACS…",
            )
        )
        assertEquals(RegistrationStep.WAITING_ACS, progress.step)
        assertEquals("Buscando CPE en GenieACS…", progress.message)
        assertFalse(progress.done)
    }

    @Test
    fun `TR-069 pending applying wifi maps to APPLYING_WIFI`() {
        val progress = RegistrationProgressMapper.from(
            SubscriptionDto(
                id = 1,
                oltProvisionStatus = OltProvisionStatus.COMPLETE,
                mikrotikProvisionStatus = MikrotikProvisionStatus.COMPLETE,
                tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
                tr069Message = "Aplicando WiFi…",
            )
        )
        assertEquals(RegistrationStep.APPLYING_WIFI, progress.step)
        assertFalse(progress.done)
    }

    @Test
    fun `TR-069 pending verifying maps to VERIFYING`() {
        val progress = RegistrationProgressMapper.from(
            SubscriptionDto(
                id = 1,
                oltProvisionStatus = OltProvisionStatus.COMPLETE,
                mikrotikProvisionStatus = MikrotikProvisionStatus.COMPLETE,
                tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
                tr069Message = "Verificando configuración…",
            )
        )
        assertEquals(RegistrationStep.VERIFYING, progress.step)
        assertFalse(progress.done)
    }

    @Test
    fun `TR-069 COMPLETE is done`() {
        val dto = SubscriptionDto(
            id = 1,
            oltProvisionStatus = OltProvisionStatus.COMPLETE,
            mikrotikProvisionStatus = MikrotikProvisionStatus.COMPLETE,
            tr069ProvisionStatus = Tr069ProvisionStatus.COMPLETE,
            provisioningPending = false,
        )
        val progress = RegistrationProgressMapper.from(dto)
        assertEquals(RegistrationStep.DONE, progress.step)
        assertTrue(progress.done)
        assertEquals(dto, progress.subscription)
    }

    @Test
    fun `TR-069 MANUAL_REQUIRED is done for overlay`() {
        val dto = SubscriptionDto(
            id = 1,
            oltProvisionStatus = OltProvisionStatus.COMPLETE,
            mikrotikProvisionStatus = MikrotikProvisionStatus.COMPLETE,
            tr069ProvisionStatus = Tr069ProvisionStatus.MANUAL_REQUIRED,
            tr069Message = "ONU no contactó al ACS",
            provisioningPending = false,
        )
        val progress = RegistrationProgressMapper.from(dto)
        assertEquals(RegistrationStep.DONE, progress.step)
        assertTrue(progress.done)
        assertEquals(dto, progress.subscription)
    }

    @Test
    fun `TR-069 NA with mk olt ok is done`() {
        val progress = RegistrationProgressMapper.from(
            SubscriptionDto(
                id = 1,
                oltProvisionStatus = OltProvisionStatus.NA,
                mikrotikProvisionStatus = MikrotikProvisionStatus.COMPLETE,
                tr069ProvisionStatus = Tr069ProvisionStatus.NA,
                provisioningPending = false,
            )
        )
        assertEquals(RegistrationStep.DONE, progress.step)
        assertTrue(progress.done)
    }

    @Test
    fun `OLT failed maps to FAILED`() {
        val progress = RegistrationProgressMapper.from(
            SubscriptionDto(
                id = 1,
                oltProvisionStatus = OltProvisionStatus.FAILED,
                mikrotikProvisionStatus = MikrotikProvisionStatus.PENDING,
                tr069ProvisionStatus = Tr069ProvisionStatus.PENDING,
            )
        )
        assertEquals(RegistrationStep.FAILED, progress.step)
        assertTrue(progress.done)
    }
}
