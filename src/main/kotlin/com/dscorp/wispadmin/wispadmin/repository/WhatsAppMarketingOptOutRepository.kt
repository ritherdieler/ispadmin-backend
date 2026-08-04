package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMarketingOptOut
import org.springframework.data.jpa.repository.JpaRepository

interface WhatsAppMarketingOptOutRepository : JpaRepository<WhatsAppMarketingOptOut, Int> {

    fun findByPhone(phone: String): WhatsAppMarketingOptOut?
}
