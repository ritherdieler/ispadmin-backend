package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuBySnResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredOnusResponseDto
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.dto.BoardInfoDto
import com.dscorp.wispadmin.oltgateway.dto.HealthResponseDto
import com.dscorp.wispadmin.oltgateway.dto.OltInfoDto
import com.dscorp.wispadmin.oltgateway.dto.OnuDetailDto
import com.dscorp.wispadmin.oltgateway.dto.OnuSummaryItemDto
import com.dscorp.wispadmin.oltgateway.dto.OnuSummaryListDto
import com.dscorp.wispadmin.oltgateway.dto.OpticalInfoDto
import com.dscorp.wispadmin.oltgateway.exception.OnuNotFoundException
import com.dscorp.wispadmin.oltgateway.mapper.SmartOltCompatMapper
import com.dscorp.wispadmin.oltgateway.parser.AutofindParser
import com.dscorp.wispadmin.oltgateway.parser.BoardParser
import com.dscorp.wispadmin.oltgateway.parser.OnuInfoBySnParser
import com.dscorp.wispadmin.oltgateway.parser.OpticalInfoParser
import com.dscorp.wispadmin.oltgateway.parser.ParsedAutofindOnt
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuBySn
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary
import com.dscorp.wispadmin.oltgateway.parser.VersionParser
import com.dscorp.wispadmin.oltgateway.service.inventory.ParallelOnuInventoryReader
import com.dscorp.wispadmin.oltgateway.snmp.GponFsp
import com.dscorp.wispadmin.oltgateway.snmp.OltSnmpClient
import com.dscorp.wispadmin.oltgateway.ssh.OltCommandExecutor
import org.slf4j.LoggerFactory

class OltGatewayQueryService(
    private val commandExecutor: OltCommandExecutor,
    private val inventoryReader: ParallelOnuInventoryReader,
    private val oltRepository: OltMgrOltRepository,
    private val smartOltCompatMapper: SmartOltCompatMapper,
    private val properties: OltGatewayProperties,
    private val versionParser: VersionParser,
    private val boardParser: BoardParser,
    private val autofindParser: AutofindParser,
    private val onuInfoBySnParser: OnuInfoBySnParser,
    private val opticalInfoParser: OpticalInfoParser,
    private val snmpClient: OltSnmpClient? = null
) : OltGatewayQueryFacade {

    companion object {
        private val logger = LoggerFactory.getLogger(OltGatewayQueryService::class.java)
    }

    private fun snmpReady(): Boolean {
        return properties.snmp.enabled &&
            properties.snmp.roCommunity.isNotBlank() &&
            snmpClient != null
    }

    override fun health(): HealthResponseDto {
        return try {
            val latency = commandExecutor.ping()
            HealthResponseDto(status = "UP", oltReachable = true, latencyMs = latency)
        } catch (ex: Exception) {
            logger.warn("OLT health check failed: {}", ex.message)
            HealthResponseDto(status = "DOWN", oltReachable = false, latencyMs = -1)
        }
    }

    override fun oltInfo(): OltInfoDto {
        return commandExecutor.adhoc { session ->
            val versionOutput = session.execute("display version")
            val version = versionParser.parse(versionOutput)
            val model = oltRepository.findByName(properties.oltId).map { it.model }.orElse(null)
            val maxProbe = model?.maxSlotProbe ?: properties.inventory.maxSlotProbe
            val boardsBySlot = linkedMapOf<Int, BoardInfoDto>()
            for (probe in 0..maxProbe) {
                try {
                    val boardOutput = session.execute("display board $probe")
                    for (parsed in boardParser.parseAll(boardOutput)) {
                        if (parsed.boardName.isBlank()) continue
                        boardsBySlot.putIfAbsent(
                            parsed.slot,
                            BoardInfoDto(slot = parsed.slot, boardName = parsed.boardName, status = parsed.status)
                        )
                    }
                } catch (_: Exception) {
                }
            }
            OltInfoDto(
                oltId = properties.oltId,
                product = version.product,
                version = version.version,
                patch = version.patch,
                uptime = version.uptime,
                boards = boardsBySlot.values.sortedBy { it.slot },
                modelCode = model?.code ?: properties.modelCode,
                maxConcurrentCliSessions = model?.maxConcurrentCliSessions ?: properties.session.poolSize
            )
        }
    }

    override fun autofind(): SmartOltUnconfiguredOnusResponseDto {
        return smartOltCompatMapper.toUnconfirmedOnuResponse(autofindParsed(), properties.oltId)
    }

    override fun bySn(sn: String): SmartOltOnuBySnResponseDto {
        val parsed = bySnParsed(sn)
        val response = smartOltCompatMapper.toOnuBySnResponse(parsed, properties.oltId)
        if (!response.status || response.onus.isEmpty()) {
            throw OnuNotFoundException("ONU not found for SN=$sn")
        }
        return response
    }

    override fun autofindParsed(): List<ParsedAutofindOnt> {
        if (snmpReady()) {
            return snmpClient!!.listAutofind()
        }
        requireSshInventoryFallback("autofind")
        return autofindViaSshDeprecated()
    }

    override fun bySnParsed(sn: String): ParsedOnuBySn? {
        val output = commandExecutor.run("display ont info by-sn $sn")
        return onuInfoBySnParser.parse(output)
    }

    override fun listOnusParsed(): List<ParsedOnuSummary> {
        if (snmpReady()) {
            return snmpClient!!.listConfiguredOnus()
        }
        requireSshInventoryFallback("listOnus")
        @Suppress("DEPRECATION")
        return inventoryReader.listOnusParsed()
    }

    override fun listOnus(): OnuSummaryListDto {
        val items = listOnusParsed().map {
            OnuSummaryItemDto(
                frame = it.frame,
                slot = it.slot,
                port = it.port,
                ontId = it.ontId,
                sn = it.sn,
                controlFlag = it.controlFlag,
                runState = it.runState,
                configState = it.configState,
                matchState = it.matchState,
                description = it.description
            )
        }
        return OnuSummaryListDto(items = items, total = items.size)
    }

    override fun onuDetail(slot: Int, port: Int, ontId: Int): OnuDetailDto {
        return commandExecutor.adhoc { session ->
            session.execute("interface gpon 0/$slot")
            val output = session.execute("display ont info $port $ontId")
            session.execute("quit")
            val parsed = onuInfoBySnParser.parse(output)
                ?: throw OnuNotFoundException("ONU not found at $slot/$port/$ontId")
            OnuDetailDto(
                sn = parsed.sn,
                frame = parsed.frame,
                slot = parsed.slot,
                port = parsed.port,
                ontId = parsed.ontId,
                description = parsed.description,
                runState = parsed.runState,
                controlFlag = parsed.controlFlag,
                lineProfileId = parsed.lineProfileId,
                lineProfileName = parsed.lineProfileName,
                serviceProfileId = parsed.serviceProfileId,
                serviceProfileName = parsed.serviceProfileName
            )
        }
    }

    override fun optical(slot: Int, port: Int, ontId: Int): OpticalInfoDto {
        if (snmpReady()) {
            return opticalViaSnmp(slot, port, ontId)
        }
        requireSshSignalFallback("optical")
        return opticalViaSshDeprecated(slot, port, ontId)
    }

    private fun opticalViaSnmp(slot: Int, port: Int, ontId: Int): OpticalInfoDto {
        val rows = snmpClient!!.listOptical(listOf(GponFsp(frame = 0, slot = slot, port = port)))
        val match = rows.firstOrNull { it.key.ontId == ontId }
            ?: throw OnuNotFoundException("ONU optical not found via SNMP at $slot/$port/$ontId")
        return OpticalInfoDto(
            slot = slot,
            port = port,
            ontId = ontId,
            rxPowerDbm = match.onuRxDbm,
            txPowerDbm = match.onuTxDbm,
            temperatureC = null,
            voltageV = null,
            biasCurrentMa = null,
            oltRxPowerDbm = match.oltRxDbm
        )
    }

    @Deprecated("SSH autofind is deprecated; use SNMP listAutofind()")
    private fun autofindViaSshDeprecated(): List<ParsedAutofindOnt> {
        val output = commandExecutor.run("display ont autofind all")
        return autofindParser.parse(output)
    }

    @Deprecated("SSH optical is deprecated; use SNMP listOptical()")
    private fun opticalViaSshDeprecated(slot: Int, port: Int, ontId: Int): OpticalInfoDto {
        return commandExecutor.adhoc { session ->
            session.execute("interface gpon 0/$slot")
            val output = session.execute("display ont optical-info $port $ontId")
            session.execute("quit")
            val parsed = opticalInfoParser.parse(output, ontId)
            OpticalInfoDto(
                slot = slot,
                port = port,
                ontId = ontId,
                rxPowerDbm = parsed.rxPowerDbm,
                txPowerDbm = parsed.txPowerDbm,
                temperatureC = parsed.temperatureC,
                voltageV = parsed.voltageV,
                biasCurrentMa = parsed.biasCurrentMa,
                oltRxPowerDbm = parsed.oltRxPowerDbm
            )
        }
    }

    private fun requireSshInventoryFallback(op: String) {
        if (properties.snmp.enabled && !properties.snmp.allowSshInventoryFallback) {
            error(
                "SNMP required for $op (set OLT_GATEWAY_SNMP_ENABLED + RO community, " +
                    "or allow-ssh-inventory-fallback=true)"
            )
        }
        if (properties.snmp.enabled) {
            logger.warn(
                "{} using deprecated SSH inventory fallback " +
                    "(enable OLT_GATEWAY_SNMP_RO_COMMUNITY / snmp client)",
                op
            )
        }
    }

    private fun requireSshSignalFallback(op: String) {
        if (properties.snmp.enabled && !properties.snmp.allowSshSignalFallback) {
            error(
                "SNMP required for $op (set OLT_GATEWAY_SNMP_ENABLED + RO community, " +
                    "or allow-ssh-signal-fallback=true)"
            )
        }
        if (properties.snmp.enabled) {
            logger.warn(
                "{} using deprecated SSH optical fallback " +
                    "(enable OLT_GATEWAY_SNMP_RO_COMMUNITY / snmp client)",
                op
            )
        }
    }
}
