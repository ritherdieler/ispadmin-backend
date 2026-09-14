package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppSyncedTemplate
import org.springframework.data.jpa.repository.JpaRepository

interface WhatsAppSyncedTemplateRepository : JpaRepository<WhatsAppSyncedTemplate, String> {

    fun findByName(name: String): WhatsAppSyncedTemplate?

    fun findByNameIn(names: Collection<String>): List<WhatsAppSyncedTemplate>

    fun findAllByOrderByNameAsc(): List<WhatsAppSyncedTemplate>
}
