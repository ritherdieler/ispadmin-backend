package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.data.model.util.GeoLocationConverter
import java.util.*
import javax.persistence.*

@Entity
data class Carrier(
    @GeneratedValue
    @Id
    val id: Int? = null,
    val name: String,
    val ruc: String,
    val phone: String,
    val email: String,
    val representedBy: String,
)