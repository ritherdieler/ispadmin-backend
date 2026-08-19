package com.dscorp.wispadmin.oltgateway.exception

open class OltGatewayException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class OltUnreachableException(message: String, cause: Throwable? = null) : OltGatewayException(message, cause)

class OnuNotFoundException(message: String) : OltGatewayException(message)

class OltCommandTimeoutException(message: String, cause: Throwable? = null) : OltGatewayException(message, cause)

class InvalidOltGatewayApiKeyException(message: String) : OltGatewayException(message)

class OltWritesDisabledException(message: String) : OltGatewayException(message)

class OltGatewayConflictException(message: String) : OltGatewayException(message)

class OltGatewayValidationException(message: String) : OltGatewayException(message)

class CliBusBusyException(val reason: String) : OltGatewayException("CLI bus busy: $reason")
