package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.dto.OutlayDTO
import java.util.*
import javax.persistence.*

@Entity
data class Outlay(
    @Id
    @GeneratedValue
    val id: Int = 0,
    val amount: Double,
    val description: String,
    val date: Date = Date(),
    val document_code: String? = null,
    val category: String? = null,
    val receipt_url: String? = null,
    val cost_center: String? = null,

    @ManyToOne
    @JoinColumn(name = "responsible_id")
    val responsible: User
) {
    fun toDto(): OutlayDTO {
        return OutlayDTO(
            id = id,
            amount = amount,
            document_code = document_code,
            description = description,
            date = date,
            category = category,
            receipt_urls = receipt_url?.split(",")?.filter { it.isNotBlank() } ?: emptyList(), // Convertir string a lista
            cost_center = cost_center,
            responsibleName = "${responsible.name} ${responsible.lastName}",
            responsibleId = responsible.id
        )
    }

}