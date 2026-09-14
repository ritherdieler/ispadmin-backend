package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.response.OnuBySnResponse
import com.dscorp.wispadmin.wispadmin.response.Response

interface OltService {
    
    fun getUnConfiguredOnus(): List<Response>?
    
    fun getOnuBySn(onuSn: String): OnuBySnResponse
    
    fun moveOnu(request: MoveOnuRequest, onu: Onu, newNapBox: NapBox)
    
    fun authorizeOnuInSmartOltWidthPostMethod(authorizationRequest: OnuAuthorizationRequest)
    
    fun deleteOnu(onuExternalId: String)

    fun rebootOnu(uniqueExternalId: String)
}
