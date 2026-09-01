package com.dscorp.wispadmin.servicehealth.controller

import com.dscorp.wispadmin.wispadmin.security.ObservabilitySessionTokenService
import org.springframework.stereotype.Component
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import javax.servlet.http.HttpServletRequest

data class HealthActor(val id: Int, val role: String)
@Component
class HealthAccess(private val tokens: ObservabilitySessionTokenService) {
    fun require(request: HttpServletRequest, admin: Boolean = false): HealthActor {
        val token=request.getHeader("Authorization")?.takeIf { it.startsWith("Bearer ",true) }?.substring(7)
        val claims=tokens.verifyAccess(token) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sesión requerida")
        val id=claims.userId ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED,"Usuario requerido")
        val role=claims.type ?: ""
        if(role !in if(admin) setOf("ADMIN") else setOf("ADMIN","TECHNICIAN")) throw ResponseStatusException(HttpStatus.FORBIDDEN,"Permiso técnico requerido")
        return HealthActor(id,role)
    }
    fun confirmation(request: HttpServletRequest): String {
        if(request.getHeader("X-Confirm-Action")!="true") throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Confirme la acción")
        return request.getHeader("Idempotency-Key")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,128}")) }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key requerido")
    }
}
