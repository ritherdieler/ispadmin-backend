package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.OnboardingV2ContactRequest
import com.dscorp.wispadmin.acs.OnboardingV2ContactResponse
import com.dscorp.wispadmin.acs.OnboardingV2ContactState
import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.genieacs.GenieAcsProperties
import com.dscorp.wispadmin.acs.genieacs.NamedCpeLayouts
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/** Read-only contact gate. A later v2 service owns task correlation and queueing. */
@Service
class OnboardingV2AcsContactService(
    private val client: GenieAcsClient,
    private val properties: GenieAcsProperties,
) {
    fun contact(request: OnboardingV2ContactRequest): OnboardingV2ContactResponse {
        require(request.operationId.matches(Regex("[a-zA-Z0-9-]{8,64}"))) { "INVALID_OPERATION_ID" }
        val serial = request.sn.trim().uppercase()
        require(serial.matches(Regex("[A-Z0-9]{12,16}"))) { "INVALID_ONU_SERIAL" }
        if (!properties.enabled) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ACS is disabled")
        }
        val matches = client.findDeviceBySerialSuffix(serial).filter { device ->
            device.serialNumber?.trim()?.equals(serial, ignoreCase = true) == true ||
                device.id.uppercase().endsWith(serial)
        }
        if (matches.isEmpty()) return OnboardingV2ContactResponse(OnboardingV2ContactState.WAITING)
        if (matches.size > 1) throw ResponseStatusException(HttpStatus.CONFLICT, "Multiple ACS devices match the ONU serial")
        val device = matches.single()
        val model = device.productClass?.trim()?.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "ACS device model is missing")
        if (!NamedCpeLayouts.supported(model)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "ACS device model is not supported by onboarding v2")
        }
        val firmware = device.softwareVersion?.trim()?.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "ACS device firmware is missing")
        return OnboardingV2ContactResponse(
            state = OnboardingV2ContactState.READY,
            deviceId = device.id,
            model = model,
            firmware = firmware,
            lastInformAt = device.lastInform,
        )
    }
}
