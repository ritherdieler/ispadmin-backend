package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.dto.CpeNetworkConfigRequest
import com.dscorp.wispadmin.wispadmin.dto.CpeWifiConfigRequest
import com.dscorp.wispadmin.wispadmin.dto.UpdateCpeConfigRequest
import org.springframework.stereotype.Component
import java.net.InetAddress

@Component
class CpeConfigValidator {

    fun validate(request: UpdateCpeConfigRequest) {
        if (request.network == null && request.wifi == null) {
            throw IllegalArgumentException("Debe enviar al menos configuración de red o Wi-Fi")
        }
        request.network?.let { validateNetwork(it) }
        request.wifi?.let { validateWifi(it) }
    }

    private fun validateNetwork(network: CpeNetworkConfigRequest) {
        val hasStaticWan = listOf(
            network.ipAddress,
            network.subnetMask,
            network.gateway,
            network.dnsPrimary,
            network.dnsSecondary,
        ).any { !it.isNullOrBlank() }

        // A VLAN-only change is valid on the OLT channel, but a partial static WAN never is.
        if (hasStaticWan) {
            requireValidIpv4(network.ipAddress, "La dirección IP no es válida")
            requireValidSubnetMask(network.subnetMask)
            requireValidIpv4(network.gateway, "La puerta de enlace no es válida")
            requireValidIpv4(network.dnsPrimary, "El DNS principal no es válido")
            network.dnsSecondary?.takeIf { it.isNotBlank() }?.let {
                requireValidIpv4(it, "El DNS secundario no es válido")
            }
        } else if (network.vlanId == null) {
            throw IllegalArgumentException("Debe enviar la VLAN o los datos de red WAN")
        }

        network.vlanId?.let { vlan ->
            if (vlan !in MIN_VLAN..MAX_VLAN) {
                throw IllegalArgumentException("El VLAN ID debe estar entre $MIN_VLAN y $MAX_VLAN")
            }
        }
    }

    private fun validateWifi(wifi: CpeWifiConfigRequest) {
        if (wifi.ssid.trim().isBlank()) {
            throw IllegalArgumentException("El nombre de red (SSID) es obligatorio")
        }
        if (wifi.password.length < MIN_WIFI_PASSWORD_LENGTH) {
            throw IllegalArgumentException(
                "La clave Wi-Fi debe tener al menos $MIN_WIFI_PASSWORD_LENGTH caracteres"
            )
        }
    }

    private fun requireValidIpv4(value: String, message: String) {
        val trimmed = value.trim()
        if (!IPV4_REGEX.matches(trimmed) || !isReachableIpv4(trimmed)) {
            throw IllegalArgumentException(message)
        }
    }

    private fun requireValidSubnetMask(value: String) {
        val trimmed = value.trim()
        if (!IPV4_REGEX.matches(trimmed) || trimmed !in VALID_SUBNET_MASKS) {
            throw IllegalArgumentException("La máscara de subred no es válida")
        }
    }

    private fun isReachableIpv4(value: String): Boolean =
        runCatching { InetAddress.getByName(value).hostAddress == value }.getOrDefault(false)

    companion object {
        const val MIN_WIFI_PASSWORD_LENGTH = 8
        private const val MIN_VLAN = 1
        private const val MAX_VLAN = 4094
        private val IPV4_REGEX = Regex("""^(?:\d{1,3}\.){3}\d{1,3}$""")

        private val VALID_SUBNET_MASKS = setOf(
            "255.255.255.255",
            "255.255.255.254",
            "255.255.255.252",
            "255.255.255.248",
            "255.255.255.240",
            "255.255.255.224",
            "255.255.255.192",
            "255.255.255.128",
            "255.255.255.0",
            "255.255.254.0",
            "255.255.252.0",
            "255.255.248.0",
            "255.255.240.0",
            "255.255.224.0",
            "255.255.192.0",
            "255.255.128.0",
            "255.255.0.0",
            "255.254.0.0",
            "255.252.0.0",
            "255.248.0.0",
            "255.240.0.0",
            "255.224.0.0",
            "255.192.0.0",
            "255.128.0.0",
            "255.0.0.0",
            "254.0.0.0",
            "252.0.0.0",
            "248.0.0.0",
            "240.0.0.0",
            "224.0.0.0",
            "192.0.0.0",
            "128.0.0.0",
            "0.0.0.0",
        )
    }
}
