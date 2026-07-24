package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription

object WhatsAppSubscriptionRowMapper {

    fun subscriptionFromRow(row: Array<Any>): Subscription {
        return Subscription(
            firstName = row.stringAt(1),
            lastName = row.stringAt(2),
            phone = row.stringAt(3),
            serviceStatus = ServiceStatus.valueOf(row.stringAt(4) ?: ServiceStatus.ACTIVE.name),
            equipmentCondition = EquipmentCondition.LOAN
        ).apply {
            id = row.intAt(0)
        }
    }

    fun buildClientName(firstName: String?, lastName: String?): String {
        return listOfNotNull(firstName?.trim()?.takeIf { it.isNotEmpty() }, lastName?.trim()?.takeIf { it.isNotEmpty() })
            .joinToString(" ")
            .ifBlank { "Cliente" }
    }

    fun Array<Any>.intAt(index: Int): Int = (this[index] as Number).toInt()

    fun Array<Any>.stringAt(index: Int): String? {
        return this.getOrNull(index)?.toString()?.takeIf { it.isNotBlank() }
    }
}
