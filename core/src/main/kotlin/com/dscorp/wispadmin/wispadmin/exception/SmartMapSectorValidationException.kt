package com.dscorp.wispadmin.wispadmin.exception

class SmartMapSectorValidationException(
    message: String,
    val code: String,
    val sectorName: String,
) : RuntimeException(message)
