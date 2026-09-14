package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.dto.CutServiceSummaryDto

interface IServiceCutManager {
    
    fun cutInternetService(): CutServiceSummaryDto
}



