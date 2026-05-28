package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.util.BaseResponse
import com.dscorp.wispadmin.wispadmin.service.MockOltService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/debug/olt")
class MockOltDebugController @Autowired constructor(
    private val mockOltService: MockOltService
) {

    @Value("\${olt.service.mock.enabled:false}")
    private lateinit var mockEnabled: String

    @GetMapping("/status")
    fun getMockStatus(): ResponseEntity<BaseResponse> {
        if (!mockEnabled.toBoolean()) {
            return ResponseEntity.ok(
                BaseResponse(
                    status = 200,
                    message = "Mock OLT no está habilitado en este ambiente",
                    data = mapOf("mockEnabled" to false),
                    error = null
                )
            )
        }

        return ResponseEntity.ok(
            BaseResponse(
                status = 200,
                message = "Estado del Mock OLT",
                data = mapOf(
                    "mockEnabled" to true,
                    "authorizedOnus" to mockOltService.getAuthorizedOnusCount(),
                    "unconfiguredOnus" to mockOltService.getUnconfiguredOnusCount(),
                    "onuDetails" to mockOltService.getOnuDetailsCount()
                ),
                error = null
            )
        )
    }

    @PostMapping("/reset")
    fun resetMockData(): ResponseEntity<BaseResponse> {
        if (!mockEnabled.toBoolean()) {
            return ResponseEntity.badRequest().body(
                BaseResponse(
                    status = 400,
                    message = "Mock OLT no está habilitado en este ambiente",
                    data = null,
                    error = "No se puede resetear datos mock en producción"
                )
            )
        }

        mockOltService.clearMockData()
        
        return ResponseEntity.ok(
            BaseResponse(
                status = 200,
                message = "Datos mock reseteados exitosamente",
                data = mapOf(
                    "authorizedOnus" to mockOltService.getAuthorizedOnusCount(),
                    "unconfiguredOnus" to mockOltService.getUnconfiguredOnusCount(),
                    "onuDetails" to mockOltService.getOnuDetailsCount()
                ),
                error = null
            )
        )
    }
}
