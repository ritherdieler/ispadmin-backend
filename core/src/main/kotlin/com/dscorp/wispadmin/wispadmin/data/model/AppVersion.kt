package com.dscorp.wispadmin.wispadmin.data.model

import java.util.*
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
data class AppVersion(
    @GeneratedValue
    @Id
    val id: Int,
    val versionCode: Int,
    val versionName: String?=null,
    val releaseDate: Date = Date(),
    val description: String?=null,
    val downloadUrl: String?=null,
)