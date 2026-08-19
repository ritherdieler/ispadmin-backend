package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.dto.CpeNetworkConfigRequest
import com.dscorp.wispadmin.wispadmin.dto.CpeWifiConfigRequest
import com.dscorp.wispadmin.wispadmin.dto.UpdateCpeConfigRequest
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CpeConfigValidatorTest {

    private val validator = CpeConfigValidator()

    @Test
    fun `validate rejects empty cpe config payload`() {
        val error = assertThrows<IllegalArgumentException> {
            validator.validate(UpdateCpeConfigRequest())
        }
        assertEquals("Debe enviar al menos configuración de red o Wi-Fi", error.message)
    }

    @Test
    fun `validate rejects invalid ipv4 address`() {
        val error = assertThrows<IllegalArgumentException> {
            validator.validate(
                UpdateCpeConfigRequest(
                    network = CpeNetworkConfigRequest(
                        ipAddress = "999.1.1.1",
                        subnetMask = "255.255.255.0",
                        gateway = "192.168.30.1",
                        dnsPrimary = "8.8.8.8",
                    )
                )
            )
        }
        assertEquals("La dirección IP no es válida", error.message)
    }

    @Test
    fun `validate rejects invalid subnet mask`() {
        val error = assertThrows<IllegalArgumentException> {
            validator.validate(
                UpdateCpeConfigRequest(
                    network = CpeNetworkConfigRequest(
                        ipAddress = "192.168.30.50",
                        subnetMask = "255.0.0",
                        gateway = "192.168.30.1",
                        dnsPrimary = "8.8.8.8",
                    )
                )
            )
        }
        assertEquals("La máscara de subred no es válida", error.message)
    }

    @Test
    fun `validate rejects vlan outside range`() {
        val error = assertThrows<IllegalArgumentException> {
            validator.validate(
                UpdateCpeConfigRequest(
                    network = CpeNetworkConfigRequest(
                        ipAddress = "192.168.30.50",
                        subnetMask = "255.255.255.0",
                        gateway = "192.168.30.1",
                        dnsPrimary = "8.8.8.8",
                        vlanId = 5000,
                    )
                )
            )
        }
        assertEquals("El VLAN ID debe estar entre 1 y 4094", error.message)
    }

    @Test
    fun `validate rejects short wifi password`() {
        val error = assertThrows<IllegalArgumentException> {
            validator.validate(
                UpdateCpeConfigRequest(
                    wifi = CpeWifiConfigRequest(ssid = "GigaFiber", password = "corta")
                )
            )
        }
        assertEquals("La clave Wi-Fi debe tener al menos 8 caracteres", error.message)
    }

    @Test
    fun `validate accepts a VLAN-only change for the OLT channel`() {
        assertDoesNotThrow {
            validator.validate(
                UpdateCpeConfigRequest(network = CpeNetworkConfigRequest(vlanId = 100))
            )
        }
    }

    @Test
    fun `validate rejects a network payload without VLAN nor WAN data`() {
        val error = assertThrows<IllegalArgumentException> {
            validator.validate(UpdateCpeConfigRequest(network = CpeNetworkConfigRequest()))
        }
        assertEquals("Debe enviar la VLAN o los datos de red WAN", error.message)
    }

    @Test
    fun `validate rejects a partial static WAN payload`() {
        val error = assertThrows<IllegalArgumentException> {
            validator.validate(
                UpdateCpeConfigRequest(
                    network = CpeNetworkConfigRequest(ipAddress = "192.168.30.50", vlanId = 100)
                )
            )
        }
        assertEquals("La máscara de subred no es válida", error.message)
    }

    @Test
    fun `validate accepts combined network and wifi payload`() {
        assertDoesNotThrow {
            validator.validate(
                UpdateCpeConfigRequest(
                    network = CpeNetworkConfigRequest(
                        ipAddress = "192.168.30.50",
                        subnetMask = "255.255.255.0",
                        gateway = "192.168.30.1",
                        dnsPrimary = "8.8.8.8",
                        dnsSecondary = "8.8.4.4",
                        vlanId = 100,
                    ),
                    wifi = CpeWifiConfigRequest(ssid = "GigaFiber-Casa", password = "clavewifi1"),
                )
            )
        }
    }
}
