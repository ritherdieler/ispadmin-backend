package com.dscorp.wispadmin.wispadmin.dto

import java.io.Serializable

data class DownloadDocumentDto(
    val name: String,
    val type: String,
    val base64: String,
):Serializable