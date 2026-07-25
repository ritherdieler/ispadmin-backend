package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Plan
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

    fun welcomeSubscriptionFromRow(row: Array<Any>): Subscription {
        val installationType = row.stringAt(5)?.let { runCatching { InstallationType.valueOf(it) }.getOrNull() }
        val plan = Plan(
            id = 0,
            name = row.stringAt(8),
            price = row.doubleAt(9),
            downloadSpeed = row.intAtOrNull(10),
            uploadSpeed = row.intAtOrNull(11),
            type = installationType
        )

        return Subscription(
            firstName = row.stringAt(1),
            lastName = row.stringAt(2),
            phone = row.stringAt(3),
            serviceStatus = ServiceStatus.valueOf(row.stringAt(4) ?: ServiceStatus.ACTIVE.name),
            price = row.doubleAt(6),
            subscriptionDatetime = WelcomeVariableMapper.parseSubscriptionDatetime(row.getOrNull(7)),
            installationType = installationType,
            plan = plan,
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

    fun Array<Any>.intAtOrNull(index: Int): Int? {
        return this.getOrNull(index)?.let { (it as Number).toInt() }
    }

    fun Array<Any>.doubleAt(index: Int): Double? {
        return this.getOrNull(index)?.let { (it as Number).toDouble() }
    }

    fun Array<Any>.stringAt(index: Int): String? {
        return this.getOrNull(index)?.toString()?.takeIf { it.isNotBlank() }
    }
}
