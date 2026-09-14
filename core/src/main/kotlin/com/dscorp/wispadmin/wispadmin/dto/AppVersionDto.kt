package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.AppVersion
import java.io.Serializable
import java.util.Date

data class AppVersionResponseDto(
    val id: Int? = null,
    val versionCode: Int? = null,
    val versionName: String? = null,
    val releaseDate: Date? = null,
    val description: String? = null,
    val downloadUrl: String? = null
) : Serializable

fun AppVersion.toResponseDto(): AppVersionResponseDto = AppVersionResponseDto(
    id = id,
    versionCode = versionCode,
    versionName = versionName,
    releaseDate = releaseDate,
    description = description,
    downloadUrl = downloadUrl
)
