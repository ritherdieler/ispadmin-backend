package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppPhoneSession
import org.springframework.data.jpa.repository.JpaRepository

interface WhatsAppPhoneSessionRepository : JpaRepository<WhatsAppPhoneSession, String>
