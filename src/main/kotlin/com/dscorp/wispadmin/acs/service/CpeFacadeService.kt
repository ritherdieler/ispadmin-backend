package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.CpeCommandResult
import com.dscorp.wispadmin.acs.CpeProvisionCommand
import com.dscorp.wispadmin.acs.CpeProvisionResult
import com.dscorp.wispadmin.acs.CpeStatus
import com.dscorp.wispadmin.acs.CpeTelemetryResult
import com.dscorp.wispadmin.acs.entity.CpeRecord
import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.genieacs.GenieAcsProperties
import com.dscorp.wispadmin.acs.genieacs.Tr069ProvisionRequest
import com.dscorp.wispadmin.acs.genieacs.Tr069ProvisioningService
import com.dscorp.wispadmin.acs.repository.CpeRecordRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CpeFacadeService(
    private val records: CpeRecordRepository,
    private val provisioningService: Tr069ProvisioningService,
    private val client: GenieAcsClient,
    private val properties: GenieAcsProperties,
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
        val outcome = provisioningService.provision(
            Tr069ProvisionRequest(
                onuSerial = command.sn,
                onuTypeName = command.onuType,
                ip = command.ip,
                ipSegment = command.ipSegment,
                wifiSsid24 = command.wifiSsid24,
                wifiPassword24 = command.wifiPassword24,
                wifiSsid5 = command.wifiSsid5,
                wifiPassword5 = command.wifiPassword5,
                wanVlanId = command.wanVlanId,
            )
        )
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
        return CpeTelemetryResult(
            sn = record.sn,
            uniqueExternalId = record.uniqueExternalId,
            cpeStatus = record.status,
            lastInformAt = record.lastInformAt?.toString(),
            productClass = record.productClass,
            wanIp = record.wanIp,
            ssid24 = record.ssid24,
            ssid5 = record.ssid5,
            softwareVersion = record.softwareVersion,
            deviceId = record.deviceId,
            message = record.message,
        )
    }

    fun reboot(sn: String): CpeCommandResult {
        val deviceId = records.findById(sn).orElse(null)?.deviceId
            ?: return CpeCommandResult(false, CpeStatus.FAILED, "unknown device")
        return try {
            val result = client.reboot(deviceId, connectionRequest = true)
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
            val result = client.refreshObject(deviceId, "InternetGatewayDevice.LANDevice.1.WLANConfiguration", connectionRequest = true)
            CpeCommandResult(result.accepted, if (result.accepted) CpeStatus.PENDING else CpeStatus.FAILED, result.body)
        } catch (ex: Exception) {
            CpeCommandResult(false, CpeStatus.FAILED, ex.message)
        }
    }
}
