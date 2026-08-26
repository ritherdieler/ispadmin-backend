package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.data.model.MikrotikProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.dto.RegistrationProgressDto
import com.dscorp.wispadmin.wispadmin.dto.RegistrationStep
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto

object RegistrationProgressMapper {

    fun from(dto: SubscriptionDto): RegistrationProgressDto {
        val subscriptionId = dto.id ?: 0
        val olt = dto.oltProvisionStatus
        val mikrotik = dto.mikrotikProvisionStatus
        val tr069 = dto.tr069ProvisionStatus
        val tr069Message = dto.tr069Message

        if (olt == OltProvisionStatus.FAILED || mikrotik == MikrotikProvisionStatus.FAILED) {
            return RegistrationProgressDto(
                subscriptionId = subscriptionId,
                step = RegistrationStep.FAILED,
                message = tr069Message?.takeIf { it.isNotBlank() }
                    ?: "Error de aprovisionamiento de red",
                done = true,
                mikrotikProvisionStatus = mikrotik,
                oltProvisionStatus = olt,
                tr069ProvisionStatus = tr069,
                tr069Message = tr069Message,
                subscription = dto,
            )
        }

        if (tr069 == Tr069ProvisionStatus.COMPLETE ||
            tr069 == Tr069ProvisionStatus.MANUAL_REQUIRED ||
            tr069 == Tr069ProvisionStatus.NA
        ) {
            if (oltIsSettled(olt) && mikrotikIsSettled(mikrotik)) {
                return done(dto, subscriptionId)
            }
        }

        if (olt == OltProvisionStatus.PENDING) {
            return RegistrationProgressDto(
                subscriptionId = subscriptionId,
                step = RegistrationStep.AUTHORIZING_ONU,
                message = "Autorizando ONU…",
                done = false,
                mikrotikProvisionStatus = mikrotik,
                oltProvisionStatus = olt,
                tr069ProvisionStatus = tr069,
                tr069Message = tr069Message,
            )
        }

        if (mikrotik == MikrotikProvisionStatus.PENDING) {
            return RegistrationProgressDto(
                subscriptionId = subscriptionId,
                step = RegistrationStep.PROVISIONING_MIKROTIK,
                message = "Configurando MikroTik…",
                done = false,
                mikrotikProvisionStatus = mikrotik,
                oltProvisionStatus = olt,
                tr069ProvisionStatus = tr069,
                tr069Message = tr069Message,
            )
        }

        if (tr069 == Tr069ProvisionStatus.PENDING) {
            val step = tr069PendingStep(tr069Message)
            return RegistrationProgressDto(
                subscriptionId = subscriptionId,
                step = step,
                message = tr069Message?.takeIf { it.isNotBlank() } ?: defaultMessage(step),
                done = false,
                mikrotikProvisionStatus = mikrotik,
                oltProvisionStatus = olt,
                tr069ProvisionStatus = tr069,
                tr069Message = tr069Message,
            )
        }

        if (!dto.provisioningPending) {
            return done(dto, subscriptionId)
        }

        return RegistrationProgressDto(
            subscriptionId = subscriptionId,
            step = RegistrationStep.REGISTERING,
            message = "Registrando…",
            done = false,
            mikrotikProvisionStatus = mikrotik,
            oltProvisionStatus = olt,
            tr069ProvisionStatus = tr069,
            tr069Message = tr069Message,
        )
    }

    private fun done(dto: SubscriptionDto, subscriptionId: Int) = RegistrationProgressDto(
        subscriptionId = subscriptionId,
        step = RegistrationStep.DONE,
        message = dto.tr069Message?.takeIf { it.isNotBlank() } ?: "Listo",
        done = true,
        mikrotikProvisionStatus = dto.mikrotikProvisionStatus,
        oltProvisionStatus = dto.oltProvisionStatus,
        tr069ProvisionStatus = dto.tr069ProvisionStatus,
        tr069Message = dto.tr069Message,
        subscription = dto,
    )

    private fun oltIsSettled(status: OltProvisionStatus?): Boolean =
        status == null ||
            status == OltProvisionStatus.COMPLETE ||
            status == OltProvisionStatus.NA

    private fun mikrotikIsSettled(status: MikrotikProvisionStatus?): Boolean =
        status == null || status == MikrotikProvisionStatus.COMPLETE

    private fun tr069PendingStep(message: String?): RegistrationStep {
        val normalized = message.orEmpty().lowercase()
        return when {
            normalized.contains("wifi") || normalized.contains("ssid") ->
                RegistrationStep.APPLYING_WIFI
            normalized.contains("verific") ->
                RegistrationStep.VERIFYING
            normalized.contains("aplicando wan") ||
                normalized.contains("configurando wan") ||
                (normalized.contains("wan") &&
                    (normalized.contains("aplic") || normalized.contains("configur"))) ->
                RegistrationStep.APPLYING_WIFI
            else -> RegistrationStep.WAITING_ACS
        }
    }

    private fun defaultMessage(step: RegistrationStep): String = when (step) {
        RegistrationStep.WAITING_ACS -> "Esperando ACS…"
        RegistrationStep.APPLYING_WIFI -> "Aplicando WiFi…"
        RegistrationStep.VERIFYING -> "Verificando…"
        else -> "Aprovisionando…"
    }
}
