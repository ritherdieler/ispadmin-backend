package com.dscorp.wispadmin.acs.genieacs

import com.dscorp.wispadmin.acs.entity.AcsModelProfileDto
import com.dscorp.wispadmin.acs.entity.AcsModelProfileImportResultDto
import com.dscorp.wispadmin.acs.entity.AcsModelProfilePreviewDto
import com.dscorp.wispadmin.acs.entity.Tr069ModelProfileRecord
import com.dscorp.wispadmin.acs.repository.Tr069ModelProfileRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class Tr069ModelProfileImportService(
    private val repository: Tr069ModelProfileRepository,
    private val registry: Tr069ModelProfileRegistry,
    private val objectMapper: ObjectMapper,
) {
    fun preview(csvContent: String): AcsModelProfilePreviewDto {
        val draft = GenieAcsCsvProfileExtractor.extract(csvContent)
        val dto = draft.toDto()
        return AcsModelProfilePreviewDto(
            draft = dto,
            readyToImport = draft.wlan24Path != null &&
                draft.wlan5Path != null &&
                draft.vlanParameters.isNotEmpty(),
        )
    }

    @Transactional
    fun importCsv(
        csvContent: String,
        importedBy: String?,
        aliases: List<String> = emptyList(),
    ): AcsModelProfileImportResultDto {
        val draft = GenieAcsCsvProfileExtractor.extract(csvContent)
        val replacedExisting = repository.existsById(draft.productClass)
        val entity = Tr069ModelProfileRecord.fromDraft(
            draft = draft,
            importedBy = importedBy,
            objectMapper = objectMapper,
            aliases = aliases,
        )
        repository.save(entity)
        registry.reload()
        return AcsModelProfileImportResultDto(
            saved = entity.toDto(objectMapper),
            replacedExisting = replacedExisting,
        )
    }

    fun listAll(): List<AcsModelProfileDto> =
        repository.findAll()
            .sortedBy { it.productClass }
            .map { it.toDto(objectMapper) }

    @Transactional
    fun delete(productClass: String): Boolean {
        if (!repository.existsById(productClass)) return false
        repository.deleteById(productClass)
        registry.reload()
        return true
    }

    private fun Tr069ProfileDraft.toDto(): AcsModelProfileDto = AcsModelProfileDto(
        productClass = productClass,
        manufacturer = manufacturer,
        wanConnectionDeviceIndex = wanConnectionDeviceIndex,
        wanIpConnectionPath = wanIpConnectionPath,
        wanGponLinkConfigPath = wanGponLinkConfigPath,
        vlanParameters = vlanParameters,
        wlan24Path = wlan24Path,
        wlan5Path = wlan5Path,
        wifiSecurityPrep = wifiSecurityPrep,
        clientWanIpConnectionPath = clientWanIpConnectionPath,
        clientVlanParameters = clientVlanParameters,
        sourceDeviceId = deviceId,
        sourceSerial = serialNumber,
        warnings = warnings,
    )
}
