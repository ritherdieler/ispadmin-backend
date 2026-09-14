package com.dscorp.wispadmin.wispadmin.data.model

import java.util.*
import javax.persistence.*

@Entity
data class ErrorLog(
    @Id
    @GeneratedValue
    var id: Int? = null,
    var module: String? = null,

    @Lob
    @Column(columnDefinition = "TEXT")
    var error: String? = null,

    var data: String? = null,

    var date: Date = Date()
)