package com.dscorp.wispadmin.wispadmin.service.onu

import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.MoveOnuRequest
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest

interface OnuOperationsPort {
    fun moveOnu(request: MoveOnuRequest, onu: Onu, newNapBox: NapBox)
    fun rebootOnuBySn(onuSn: String)
    fun deleteOnu(onuExternalId: String)
    fun deleteOnuBySn(onuSn: String)
    fun authorizeOnuInSmartOltWidthPostMethod(authorizationRequest: OnuAuthorizationRequest)
}
