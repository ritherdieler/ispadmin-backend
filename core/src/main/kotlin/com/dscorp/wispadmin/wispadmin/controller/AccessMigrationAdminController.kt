package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.security.CrmAccessPolicy
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.subscription.AccessMigrationQuarantineJob
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/admin/access-migration")
class AccessMigrationAdminController(
    private val quarantineJob: AccessMigrationQuarantineJob,
) {
    @PostMapping("/quarantine/finish-due")
    fun finishDueQuarantines(httpRequest: HttpServletRequest): ResponseEntity<Any> {
        if (!CrmAccessPolicy.canManageCrmSecrets(
                httpRequest.getAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE)?.toString(),
            )
        ) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Solo ADMIN puede cerrar cuarentenas PPPoE"))
        }
        val processed = quarantineJob.finishDueQuarantines()
        return ResponseEntity.ok(mapOf("processed" to processed))
    }
}
