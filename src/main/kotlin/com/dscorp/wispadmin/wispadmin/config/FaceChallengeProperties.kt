package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties

// Nivel B de anti-spoofing: el frontend completa un reto activo (giro de cabeza) y el backend
// valida un challengeToken de un solo uso antes de registrar asistencia. Esto cierra el bypass
// de llamadas directas a la API con una foto estatica.
@ConfigurationProperties(prefix = "face.challenge")
class FaceChallengeProperties {
    var enabled: Boolean = true
    var ttlMs: Long = 60_000L
    var requireForVerify: Boolean = true
    var requireForIdentify: Boolean = false
    var maxActive: Int = 5_000
}
