package com.dscorp.wispadmin.wispadmin.service.whatsapp

class WhatsAppMessageNotFoundException(
    val identifier: String,
) : RuntimeException("Mensaje no encontrado con identifier: $identifier")
