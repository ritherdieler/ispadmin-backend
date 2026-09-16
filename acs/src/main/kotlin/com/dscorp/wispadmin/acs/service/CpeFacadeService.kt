package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.CpeAccessLayout
import com.dscorp.wispadmin.acs.CpeCommandResult
import com.dscorp.wispadmin.acs.CpeProvisionCommand
import com.dscorp.wispadmin.acs.CpeProvisionResult
import com.dscorp.wispadmin.acs.CpeStatus
import com.dscorp.wispadmin.acs.CpeTelemetryResult
import com.dscorp.wispadmin.acs.CpeWifiCommand
import com.dscorp.wispadmin.acs.entity.CpeRecord
import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.genieacs.GenieAcsProperties
import com.dscorp.wispadmin.acs.genieacs.NamedCpeLayouts
import com.dscorp.wispadmin.acs.genieacs.NamedCpeProvisioner
import com.dscorp.wispadmin.acs.genieacs.NamedGenieAcsProvisions
import com.dscorp.wispadmin.acs.genieacs.Tr069ProvisionOutcome
import com.dscorp.wispadmin.acs.genieacs.Tr069ProvisionRequest
import com.dscorp.wispadmin.acs.genieacs.Tr069SerialMatcher
import com.dscorp.wispadmin.acs.repository.CpeRecordRepository
import com.dscorp.wispadmin.transport.RegistrationTiming
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CpeFacadeService(
    private val records: CpeRecordRepository,
    private val client: GenieAcsClient,
    private val properties: GenieAcsProperties,
    private val namedProvisioner: NamedCpeProvisioner? = null,
    private val timing: RegistrationTiming = RegistrationTiming.NOOP,
) {
    private companion object {
        const val WIFI_TELEMETRY_PROVISION = "gigafiber-wifi-telemetry"
    }

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
        val outcome = timing.span("acs.provision", mapOf("sn" to command.sn)) {
            namedProvisioner?.provision(request)
                ?: Tr069ProvisionOutcome(
                    status = CpeStatus.FAILED,
                    error = "named provisioner missing",
                    message = "named provisioner missing",
                )
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
        return CpeAccessLayout(
            sn = sn,
            productClass = productClass,
            connectionRequestUrl = device?.connectionRequestUrl,
            lastInformAt = device?.lastInform ?: record?.lastInformAt?.toString(),
            wanIpPath = null,
            wanPppPath = null,
            hasPppPath = NamedCpeLayouts.supported(productClass),
            wanIpSharesPppSlot = false,
        )
    }

    fun reboot(sn: String): CpeCommandResult {
        val deviceId = records.findById(sn).orElse(null)?.deviceId
            ?: return CpeCommandResult(false, CpeStatus.FAILED, "unknown device")
        return try {
            val result = client.enqueueProvisions(
                deviceId,
                NamedGenieAcsProvisions.REBOOT,
                emptyList(),
                connectionRequest = true,
            )
            CpeCommandResult(result.accepted, if (result.accepted) CpeStatus.PENDING else CpeStatus.FAILED, result.body)
        } catch (ex: Exception) {
            CpeCommandResult(false, CpeStatus.FAILED, ex.message)
        }
    }

    fun setWifi(sn: String, command: CpeWifiCommand): CpeCommandResult {
        val provisioner = namedProvisioner
            ?: return CpeCommandResult(false, CpeStatus.FAILED, "named provisioner missing")
        return provisioner.setWifi(sn, command.ssid24, command.ssid5, command.passphrase.orEmpty())
    }

    /**
     * A manual refresh does not read anything. It forces a Connection Request
     * that runs the Inform provision, and the resulting session delivers the
     * data through the same path as a periodic Inform. Parallel GPV reads would
     * see the previous session's tree and compete with the one source of truth.
     */
    fun wifiRefresh(sn: String): CpeCommandResult {
        val record = records.findById(sn).orElse(null)
            ?: return CpeCommandResult(false, CpeStatus.FAILED, "unknown SN")
        val deviceId = record.deviceId ?: return CpeCommandResult(false, CpeStatus.FAILED, "unknown device")
        return try {
            val result = client.enqueueProvisions(deviceId, WIFI_TELEMETRY_PROVISION, connectionRequest = true)
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
