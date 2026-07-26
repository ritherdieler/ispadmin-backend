package com.dscorp.wispadmin.netdiag.exception

open class NetDiagException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class IncidentNotFoundException(message: String) : NetDiagException(message)

class InvalidNetDiagApiKeyException(message: String) : NetDiagException(message)

class NetDiagConflictException(message: String) : NetDiagException(message)
