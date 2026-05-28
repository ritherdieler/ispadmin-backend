package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.Outlay
import com.dscorp.wispadmin.wispadmin.data.model.User
import java.util.Date

data class OutlayDTO(
    val id: Int? = null,
    val amount: Double,
    val document_code: String? = null,
    val description: String,
    val date: Date,
    val category: String? = null,
    val receipt_urls: List<String> = emptyList(),
    val cost_center: String? = null,
    val responsibleName: String? = null,
    val responsibleId: Int? = null
) {
    fun toEntity(user: User): Outlay {
        return Outlay(
            amount = amount, 
            description = description, 
            responsible = user, 
            document_code = document_code ?: "", 
            date = date,
            category = category,
            receipt_url = receipt_urls.joinToString(","), // Convertir lista a string separado por comas
            cost_center = cost_center
        )
    }
}