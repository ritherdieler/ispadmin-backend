package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.response.OnuBySnResponse
import com.dscorp.wispadmin.wispadmin.response.Response

/**
 * Outcome of a WAN write on the OLT. Failures are reported as warnings instead of
 * exceptions so the caller can still complete the other provisioning channels.
 */
data class OnuWanUpdateResult(
    val applied: Boolean,
    val uniqueExternalId: String? = null,
    val warnings: List<String> = emptyList(),
)

interface OltService {

    fun getUnConfiguredOnus(): List<Response>?

    fun getOnuBySn(onuSn: String): OnuBySnResponse

    fun moveOnu(request: MoveOnuRequest, onu: Onu, newNapBox: NapBox)

    fun authorizeOnuInSmartOltWidthPostMethod(authorizationRequest: OnuAuthorizationRequest)

    fun deleteOnu(onuExternalId: String)

    fun rebootOnu(uniqueExternalId: String)

    /** Updates WAN/VLAN of an already authorized ONU through the OLT (OMCI), not CWMP. */
    fun updateOnuWanConfig(
        sn: String,
        vlan: Int?,
        ip: String?,
        mask: String?,
        gateway: String?,
        dns1: String?,
        dns2: String?,
    ): OnuWanUpdateResult
}
