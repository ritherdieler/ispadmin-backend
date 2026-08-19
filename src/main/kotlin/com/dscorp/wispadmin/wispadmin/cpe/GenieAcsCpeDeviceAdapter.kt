package com.dscorp.wispadmin.wispadmin.cpe

import com.dscorp.wispadmin.wispadmin.dto.CpeNetworkConfigRequest
import com.dscorp.wispadmin.wispadmin.genieacs.GenieAcsClient
import org.springframework.stereotype.Component

@Component
class GenieAcsCpeDeviceAdapter(
    private val genieAcsClient: GenieAcsClient,
) : CpeDeviceAdapter {

    override fun describe(serialNumber: String): CpeDeviceDescriptor? =
        genieAcsClient.findDeviceDescriptor(serialNumber)?.let { device ->
            CpeDeviceDescriptor(
                serialNumber = serialNumber,
                deviceId = device.deviceId,
                vendor = device.vendor,
                model = device.model,
                lastInform = device.lastInform,
                reachable = device.reachable,
            )
        }

    override fun applyConfiguration(
        serialNumber: String,
        network: CpeNetworkConfigRequest?,
        ssid: String?,
        passphrase: String?,
    ): CpeConfigurationOutcome {
        val result = genieAcsClient.applyCpeConfiguration(
            sn = serialNumber,
            network = network,
            ssid = ssid,
            passphrase = passphrase,
        )
        return CpeConfigurationOutcome(
            appliedNetwork = result.appliedNetwork,
            appliedWifi = result.appliedWifi,
            warnings = result.warnings,
        )
    }
}
