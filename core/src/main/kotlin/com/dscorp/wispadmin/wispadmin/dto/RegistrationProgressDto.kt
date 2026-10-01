package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.MikrotikProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus

enum class RegistrationStep {
    REGISTERING,
    AUTHORIZING_ONU,
    PROVISIONING_MIKROTIK,
    WAITING_ACS,
    APPLYING_WIFI,
    VERIFYING,
    DONE,
    FAILED,
}

data class RegistrationProgressDto(
    val subscriptionId: Int,
    val step: RegistrationStep,
    val message: String,
    val done: Boolean,
    val mikrotikProvisionStatus: MikrotikProvisionStatus? = null,
    val oltProvisionStatus: OltProvisionStatus? = null,
    val tr069ProvisionStatus: Tr069ProvisionStatus? = null,
    val tr069Message: String? = null,
    val provisioningCheckpoints: List<RegistrationProgressCheckpointDto> = emptyList(),
    val subscription: SubscriptionDto? = null,
    val outcome: String? = null,
    val operationId: String? = null,
)

data class RegistrationProgressCheckpointDto(
    val stage: String,
    val state: String,
    val attempts: Int,
    val failure: RegistrationProgressFailureDto? = null,
)

data class RegistrationProgressFailureDto(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val technicalDetails: String? = null,
)
