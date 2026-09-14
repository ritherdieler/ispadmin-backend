package com.dscorp.wispadmin.wispadmin.service.mikrotik

import com.dscorp.wispadmin.wispadmin.dto.AddressListGenerationResultDto

interface IAddressListManager {
    
    fun generateAddressListForCancelledSubscriptions(): AddressListGenerationResultDto
}



