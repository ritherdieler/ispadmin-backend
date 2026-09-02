package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.api.SmartOltOnuBySnResponseDto
import com.dscorp.wispadmin.oltgateway.api.SmartOltUnconfiguredOnusResponseDto
import com.dscorp.wispadmin.oltgateway.dto.HealthResponseDto
import com.dscorp.wispadmin.oltgateway.dto.OltInfoDto
import com.dscorp.wispadmin.oltgateway.dto.OnuDetailDto
import com.dscorp.wispadmin.oltgateway.dto.OnuSummaryListDto
import com.dscorp.wispadmin.oltgateway.dto.OpticalInfoDto
import com.dscorp.wispadmin.oltgateway.parser.ParsedAutofindOnt
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuBySn
import com.dscorp.wispadmin.oltgateway.parser.ParsedOnuSummary

interface OltGatewayQueryFacade {
    fun health(): HealthResponseDto
    fun oltInfo(): OltInfoDto
    fun autofind(): SmartOltUnconfiguredOnusResponseDto
    fun bySn(sn: String): SmartOltOnuBySnResponseDto
    fun autofindParsed(): List<ParsedAutofindOnt>

    /** Refresco periódico: carril de fondo, nunca compite con el técnico en campo. */
    fun autofindParsedBackground(): List<ParsedAutofindOnt> = autofindParsed()

    /** Botón de refrescar: carril interactivo con timeout duro. */
    fun autofindParsedLive(commandTimeoutMs: Long): List<ParsedAutofindOnt> = autofindParsed()

    fun bySnParsed(sn: String): ParsedOnuBySn?
    fun listOnusParsed(): List<ParsedOnuSummary>
    fun listOnus(): OnuSummaryListDto
    fun onuDetail(slot: Int, port: Int, ontId: Int): OnuDetailDto
    fun optical(slot: Int, port: Int, ontId: Int): OpticalInfoDto
}
