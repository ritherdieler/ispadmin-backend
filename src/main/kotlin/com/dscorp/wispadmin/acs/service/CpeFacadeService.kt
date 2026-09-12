package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.CpeAccessLayout
import com.dscorp.wispadmin.acs.CpeCommandResult
import com.dscorp.wispadmin.acs.CpeProvisionCommand
import com.dscorp.wispadmin.acs.CpeProvisionResult
import com.dscorp.wispadmin.acs.CpeStatus
import com.dscorp.wispadmin.acs.CpeTelemetryResult
import com.dscorp.wispadmin.acs.entity.CpeRecord
import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.genieacs.GenieAcsProperties
import com.dscorp.wispadmin.acs.genieacs.GfVirtualParameters
import com.dscorp.wispadmin.acs.genieacs.Tr069ParameterValue
import com.dscorp.wispadmin.acs.genieacs.Tr069ProvisionRequest
import com.dscorp.wispadmin.acs.genieacs.Tr069ProvisioningService
import com.dscorp.wispadmin.acs.genieacs.Tr069ModelProfileRegistry
import com.dscorp.wispadmin.acs.genieacs.Tr069SerialMatcher
import com.dscorp.wispadmin.acs.genieacs.VparamProvisioner
import com.dscorp.wispadmin.acs.repository.CpeRecordRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CpeFacadeService(
    private val records: CpeRecordRepository,
    private val provisioningService: Tr069ProvisioningService,
    private val client: GenieAcsClient,
    private val properties: GenieAcsProperties,
    private val profiles: Tr069ModelProfileRegistry? = null,
    private val vparamProvisioner: VparamProvisioner? = null,
) {
    fun provision(command: CpeProvisionCommand): CpeProvisionResult {
        val record = records.findById(command.sn).orElseGet { CpeRecord(sn = command.sn) }
        record.uniqueExternalId = command.uniqueExternalId ?: record.uniqueExternalId
        record.status = CpeStatus.PENDING
        record.updatedAt = Instant.now()
        records.save(record)
        if (!properties.enabled) {
            record.status = CpeStatus.NA
            record.message = "ACS disabled"
            record.updatedAt = Instant.now()
            records.save(record)
            return CpeProvisionResult(command.sn, CpeStatus.NA, "ACS disabled")
        }
        val request = toProvisionRequest(command)
        val outcome = if (properties.vparams.enabled) {
            val provisioner = vparamProvisioner
                ?: return CpeProvisionResult(command.sn, CpeStatus.FAILED, "vparams provisioner missing")
            provisioner.provision(request)
        } else {
            provisioningService.provision(request)
        }
        record.status = outcome.status
        record.message = outcome.error ?: outcome.message
        record.deviceId = outcome.deviceId
        record.productClass = outcome.acsSnapshot?.productClass
        record.wanIp = outcome.acsSnapshot?.wanIpCache
        record.ssid24 = outcome.acsSnapshot?.ssid24
        record.ssid5 = outcome.acsSnapshot?.ssid5
        record.softwareVersion = outcome.acsSnapshot?.softwareVersion
        record.updatedAt = Instant.now()
        records.save(record)
        return CpeProvisionResult(command.sn, outcome.status, record.message, outcome.deviceId)
    }

    fun status(sn: String): CpeProvisionResult {
        val record = records.findById(sn).orElse(null)
            ?: return CpeProvisionResult(sn, CpeStatus.NA, "unknown SN")
        return CpeProvisionResult(record.sn, record.status, record.message, record.deviceId)
    }

    fun telemetry(sn: String): CpeTelemetryResult {
        val record = records.findById(sn).orElse(null)
            ?: return CpeTelemetryResult(sn = sn, cpeStatus = CpeStatus.NA)
        val lastInformAt = refreshLastInform(record)
        return CpeTelemetryResult(
            sn = record.sn,
            uniqueExternalId = record.uniqueExternalId,
            cpeStatus = record.status,
            lastInformAt = lastInformAt?.toString(),
            productClass = record.productClass,
            wanIp = record.wanIp,
            ssid24 = record.ssid24,
            ssid5 = record.ssid5,
            softwareVersion = record.softwareVersion,
            deviceId = record.deviceId,
            message = record.message,
            wifiAssociated2g = record.wifiAssociated2g,
            wifiAssociated5g = record.wifiAssociated5g,
            wifiAssociatedTotal = record.wifiAssociatedTotal,
            wifiObservedAt = record.wifiObservedAt?.toString(),
            wifiQualityStatus = record.wifiQualityStatus,
        )
    }

    private fun refreshLastInform(record: CpeRecord): Instant? {
        if (!properties.enabled) return record.lastInformAt
        val suffix = com.dscorp.wispadmin.acs.genieacs.Tr069SerialMatcher.normalizeSuffix(record.sn) ?: return record.lastInformAt
        val fromGenie = runCatching {
            client.findDeviceBySerialSuffix(suffix)
                .firstOrNull { it.id == record.deviceId || record.deviceId.isNullOrBlank() }
                ?.lastInform
                ?.takeIf { it.isNotBlank() }
                ?.let { Instant.parse(it) }
        }.getOrNull()
        if (fromGenie != null && fromGenie != record.lastInformAt) {
            record.lastInformAt = fromGenie
            record.updatedAt = Instant.now()
            records.save(record)
        }
        return fromGenie ?: record.lastInformAt
    }

    fun accessLayout(sn: String): CpeAccessLayout {
        val record = records.findById(sn).orElse(null)
        val suffix = Tr069SerialMatcher.normalizeSuffix(sn)
        val device = if (properties.enabled && suffix != null) {
            runCatching { client.findDeviceBySerialSuffix(suffix).firstOrNull() }.getOrNull()
        } else {
            null
        }
        val productClass = device?.productClass ?: record?.productClass
        if (properties.vparams.enabled) {
            return CpeAccessLayout(
                sn = sn,
                productClass = productClass,
                connectionRequestUrl = device?.connectionRequestUrl,
                lastInformAt = device?.lastInform ?: record?.lastInformAt?.toString(),
                wanIpPath = null,
                wanPppPath = null,
                hasPppPath = GfVirtualParameters.supportedPppProductClass(productClass),
                wanIpSharesPppSlot = false,
            )
        }
        val profile = profiles?.resolve(null, productClass)
        val pppPath = profile?.clientWanPppConnectionPath?.takeIf { it.isNotBlank() }
        val wanIpPath = profile?.forClientInternetWan(properties.clientWanIndex)?.wanIpConnectionPath
        val wcdInstance = pppPath
            ?.substringBefore(".WANIPConnection")
            ?.substringBefore(".WANPPPConnection")
        val shares = wanIpPath != null && wcdInstance != null && wanIpPath.startsWith("$wcdInstance.")
        return CpeAccessLayout(
            sn = sn,
            productClass = productClass,
            connectionRequestUrl = device?.connectionRequestUrl,
            lastInformAt = device?.lastInform ?: record?.lastInformAt?.toString(),
            wanIpPath = wanIpPath,
            wanPppPath = pppPath,
            hasPppPath = pppPath != null,
            wanIpSharesPppSlot = shares,
        )
    }

    fun reboot(sn: String): CpeCommandResult {
        val deviceId = records.findById(sn).orElse(null)?.deviceId
            ?: return CpeCommandResult(false, CpeStatus.FAILED, "unknown device")
        return try {
            val result = if (properties.vparams.enabled) {
                client.setParameterValues(
                    deviceId,
                    listOf(Tr069ParameterValue(GfVirtualParameters.REBOOT, """{"requested":true}""", "xsd:string")),
                    connectionRequest = true,
                )
            } else {
                client.reboot(deviceId, connectionRequest = true)
            }
            CpeCommandResult(result.accepted, if (result.accepted) CpeStatus.PENDING else CpeStatus.FAILED, result.body)
        } catch (ex: Exception) {
            CpeCommandResult(false, CpeStatus.FAILED, ex.message)
        }
    }

    fun wifiRefresh(sn: String): CpeCommandResult {
        val record = records.findById(sn).orElse(null)
            ?: return CpeCommandResult(false, CpeStatus.FAILED, "unknown SN")
        val deviceId = record.deviceId ?: return CpeCommandResult(false, CpeStatus.FAILED, "unknown device")
        return try {
            val result = if (properties.vparams.enabled) {
                client.getParameterValues(
                    deviceId,
                    listOf(GfVirtualParameters.WIFI_STATUS),
                    connectionRequest = true,
                )
            } else {
                client.refreshObject(
                    deviceId,
                    "InternetGatewayDevice.LANDevice.1.WLANConfiguration",
                    connectionRequest = true,
                )
            }
            CpeCommandResult(result.accepted, if (result.accepted) CpeStatus.PENDING else CpeStatus.FAILED, result.body)
        } catch (ex: Exception) {
            CpeCommandResult(false, CpeStatus.FAILED, ex.message)
        }
    }

    private fun toProvisionRequest(command: CpeProvisionCommand) = Tr069ProvisionRequest(
        onuSerial = command.sn,
        onuTypeName = command.onuType,
        ip = command.ip,
        ipSegment = command.ipSegment,
        wifiSsid24 = command.wifiSsid24,
        wifiPassword24 = command.wifiPassword24,
        wifiSsid5 = command.wifiSsid5,
        wifiPassword5 = command.wifiPassword5,
        wanVlanId = command.wanVlanId,
        pppoeUsername = command.pppoeUsername,
        pppoePassword = command.pppoePassword,
    )
}
