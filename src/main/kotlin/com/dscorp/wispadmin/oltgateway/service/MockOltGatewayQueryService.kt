package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuBySnResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredOnusResponseDto
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
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
import com.dscorp.wispadmin.oltgateway.parser.OnuSummaryParser
import com.dscorp.wispadmin.oltgateway.parser.OpticalInfoParser
import com.dscorp.wispadmin.oltgateway.parser.ParsedAutofindOnt
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuBySn
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary
import com.dscorp.wispadmin.oltgateway.parser.VersionParser
import org.slf4j.LoggerFactory

class MockOltGatewayQueryService(
    private val properties: OltGatewayProperties,
    private val smartOltCompatMapper: SmartOltCompatMapper,
    private val versionParser: VersionParser,
    private val boardParser: BoardParser,
    private val autofindParser: AutofindParser,
    private val onuInfoBySnParser: OnuInfoBySnParser,
    private val onuSummaryParser: OnuSummaryParser,
    private val opticalInfoParser: OpticalInfoParser
) : OltGatewayQueryFacade {

    companion object {
        private val logger = LoggerFactory.getLogger(MockOltGatewayQueryService::class.java)
    }

    override fun health(): HealthResponseDto {
        logger.info("MOCK OLT gateway health")
        return HealthResponseDto(status = "UP", oltReachable = true, latencyMs = 1)
    }

    override fun oltInfo(): OltInfoDto {
        val version = versionParser.parse(MockCliFixtures.DISPLAY_VERSION)
        val boards = listOf(
            boardParser.parse(MockCliFixtures.DISPLAY_BOARD_0, 0),
            boardParser.parse(MockCliFixtures.DISPLAY_BOARD_1, 1)
        ).map { BoardInfoDto(it.slot, it.boardName, it.status) }
        return OltInfoDto(
            oltId = properties.oltId,
            product = version.product,
            version = version.version,
            patch = version.patch,
            uptime = version.uptime,
            boards = boards,
            modelCode = properties.modelCode,
            maxConcurrentCliSessions = properties.session.poolSize
        )
    }

    override fun autofind(): SmartOltUnconfiguredOnusResponseDto {
        return smartOltCompatMapper.toUnconfirmedOnuResponse(autofindParsed(), properties.oltId)
    }

    override fun bySn(sn: String): SmartOltOnuBySnResponseDto {
        val parsed = bySnParsed(sn)
        if (parsed == null || !parsed.sn.equals(sn, ignoreCase = true)) {
            throw OnuNotFoundException("ONU not found for SN=$sn")
        }
        return smartOltCompatMapper.toOnuBySnResponse(parsed, properties.oltId)
    }

    override fun autofindParsed(): List<ParsedAutofindOnt> {
        return autofindParser.parse(MockCliFixtures.DISPLAY_AUTOFIND)
    }

    override fun bySnParsed(sn: String): ParsedOnuBySn? {
        val parsed = onuInfoBySnParser.parse(MockCliFixtures.DISPLAY_BY_SN)
        if (parsed == null || !parsed.sn.equals(sn, ignoreCase = true)) {
            return null
        }
        return parsed
    }

    override fun listOnusParsed(): List<ParsedOnuSummary> {
        return onuSummaryParser.parse(MockCliFixtures.DISPLAY_SUMMARY)
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
        val parsed = onuInfoBySnParser.parse(MockCliFixtures.DISPLAY_DETAIL)
            ?: throw OnuNotFoundException("ONU not found at $slot/$port/$ontId")
        return OnuDetailDto(
            sn = parsed.sn,
            frame = parsed.frame,
            slot = slot,
            port = port,
            ontId = ontId,
            description = parsed.description,
            runState = parsed.runState,
            controlFlag = parsed.controlFlag,
            lineProfileId = parsed.lineProfileId,
            lineProfileName = parsed.lineProfileName,
            serviceProfileId = parsed.serviceProfileId,
            serviceProfileName = parsed.serviceProfileName
        )
    }

    override fun optical(slot: Int, port: Int, ontId: Int): OpticalInfoDto {
        val parsed = opticalInfoParser.parse(MockCliFixtures.DISPLAY_OPTICAL, ontId)
        return OpticalInfoDto(
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
