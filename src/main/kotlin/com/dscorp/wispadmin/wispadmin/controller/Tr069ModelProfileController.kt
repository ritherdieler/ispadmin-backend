package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.security.CrmAccessPolicy
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.dscorp.wispadmin.wispadmin.service.genieacs.Tr069ModelProfileImportService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/admin/tr069-profiles")
class Tr069ModelProfileController(
    private val importService: Tr069ModelProfileImportService,
) {

    @GetMapping
    fun list(httpRequest: HttpServletRequest): ResponseEntity<Any> {
        if (!isAdmin(httpRequest)) return forbidden()
        return ResponseEntity.ok(importService.listAll())
    }

    @PostMapping("/preview")
    fun preview(
        @RequestParam("file") file: MultipartFile,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Any> {
        if (!isAdmin(httpRequest)) return forbidden()
        return try {
            ResponseEntity.ok(importService.preview(readCsv(file)))
        } catch (ex: Exception) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to (ex.message ?: "No se pudo analizar el CSV de GenieACS")))
        }
    }

    @PostMapping("/import")
    fun importProfile(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("aliases", required = false) aliases: String?,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Any> {
        if (!isAdmin(httpRequest)) return forbidden()
        return try {
            val aliasList = aliases?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            val result = importService.importCsv(
                csvContent = readCsv(file),
                importedBy = resolveUsername(httpRequest),
                aliases = aliasList,
            )
            ResponseEntity.ok(result)
        } catch (ex: Exception) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to (ex.message ?: "No se pudo importar el perfil TR-069")))
        }
    }

    @DeleteMapping("/{productClass}")
    fun delete(
        @PathVariable productClass: String,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Any> {
        if (!isAdmin(httpRequest)) return forbidden()
        val deleted = importService.delete(productClass)
        return if (deleted) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "Perfil no encontrado: $productClass"))
        }
    }

    private fun readCsv(file: MultipartFile): String {
        if (file.isEmpty) error("El archivo CSV está vacío")
        return file.inputStream.bufferedReader().readText()
    }

    private fun isAdmin(request: HttpServletRequest): Boolean =
        CrmAccessPolicy.canManageCrmSecrets(
            request.getAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE)?.toString(),
        )

    private fun resolveUsername(request: HttpServletRequest): String? =
        request.getAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE)?.toString()

    private fun forbidden(): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(mapOf("error" to "Solo ADMIN puede gestionar perfiles TR-069"))
}
