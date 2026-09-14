package com.dscorp.wispadmin.wispadmin.service

class MapboxDirectionsException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
