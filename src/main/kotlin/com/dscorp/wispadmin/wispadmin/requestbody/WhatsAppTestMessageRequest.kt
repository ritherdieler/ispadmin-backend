package com.dscorp.wispadmin.wispadmin.requestbody

// Datos minimos para probar el envio de un mensaje por WhatsApp Cloud API.
data class WhatsAppTestMessageRequest(
    val phoneNumber: String,
    val message: String
)