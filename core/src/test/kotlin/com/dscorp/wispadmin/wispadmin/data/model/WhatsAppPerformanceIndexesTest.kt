package com.dscorp.wispadmin.wispadmin.data.model

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.persistence.Table

class WhatsAppPerformanceIndexesTest {

    private fun indexNames(clazz: Class<*>): Set<String> {
        val table = clazz.getAnnotation(Table::class.java)
        return table.indexes.map { it.name }.toSet()
    }

    @Test
    fun `WhatsAppMessageLog declara indices batch para analytics y candidatos`() {
        val names = indexNames(WhatsAppMessageLog::class.java)
        assertTrue(names.contains("idx_wa_message_log_subscription_type_created"))
        assertTrue(names.contains("idx_wa_message_log_payment_type_created"))
        assertTrue(names.contains("idx_wa_message_log_campaign_id"))
        assertTrue(names.contains("idx_wa_message_log_type_created"))
    }

    @Test
    fun `WhatsAppInboundMessage declara indice de no leidos por telefono`() {
        val names = indexNames(WhatsAppInboundMessage::class.java)
        assertTrue(names.contains("idx_wa_inbound_phone_read_at"))
    }

    @Test
    fun `WhatsAppAuditLog declara indice compuesto de accion y fecha`() {
        val names = indexNames(WhatsAppAuditLog::class.java)
        assertTrue(names.contains("idx_wa_audit_action_created"))
    }
}
