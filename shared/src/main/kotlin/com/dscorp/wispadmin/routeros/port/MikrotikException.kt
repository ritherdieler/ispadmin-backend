package com.dscorp.wispadmin.routeros.port

sealed class MikrotikException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class MikrotikUnreachableException(
    message: String,
    cause: Throwable? = null
) : MikrotikException(message, cause)

class MikrotikAuthException(
    message: String,
    cause: Throwable? = null
) : MikrotikException(message, cause)

class MikrotikTimeoutException(
    message: String,
    cause: Throwable? = null
) : MikrotikException(message, cause)

class MikrotikCommandException(
    message: String,
    cause: Throwable? = null
) : MikrotikException(message, cause)
