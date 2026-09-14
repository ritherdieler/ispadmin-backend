package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.data.model.util.GeoLocationConverter
import java.util.*
import javax.persistence.*

@Entity
data class CorporateClient(
    @GeneratedValue
    @Id
    val id: Int? = null,

    @ManyToOne
    val carrier: Carrier? = null,

    //BUSINESS DATA
    val name: String,
    val address: String,
    val phone: String,
    val email: String,
    val ruc: String,
    val representedBy: String,

    @Convert(converter = GeoLocationConverter::class)
    @Column(columnDefinition = "json")
    val location: GeoLocation? = null,

    val billingDate: Date,
    val invoicedAmount: Double,
    @Enumerated(EnumType.STRING)
    val currency: CurrencyType,

    val createdAt: Date = Date(),
    val active: Boolean = true,
)

enum class CurrencyType {
    PEN, USD
}