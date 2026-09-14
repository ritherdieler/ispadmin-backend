package com.dscorp.wispadmin.acs.controller

import com.dscorp.wispadmin.acs.entity.AcsModelProfileDto
import com.dscorp.wispadmin.acs.entity.AcsProfileCsvCommand
import com.dscorp.wispadmin.acs.genieacs.Tr069ModelProfileImportService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/acs/v1/profiles")
class AcsProfileController(
    private val importService: Tr069ModelProfileImportService,
) {
    @GetMapping
    fun list(): List<AcsModelProfileDto> = importService.listAll()

    @PostMapping("/preview")
    fun preview(@RequestBody command: AcsProfileCsvCommand): ResponseEntity<Any> = try {
        ResponseEntity.ok(importService.preview(command.csv))
    } catch (ex: Exception) {
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to (ex.message ?: "No se pudo analizar el CSV de GenieACS")))
    }

    @PostMapping("/import")
    fun importProfile(@RequestBody command: AcsProfileCsvCommand): ResponseEntity<Any> = try {
        ResponseEntity.ok(
            importService.importCsv(
                csvContent = command.csv,
                importedBy = command.importedBy,
                aliases = command.aliases,
            ),
        )
    } catch (ex: Exception) {
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to (ex.message ?: "No se pudo importar el perfil TR-069")))
    }

    @DeleteMapping("/{productClass}")
    fun delete(@PathVariable productClass: String): ResponseEntity<Any> {
        val deleted = importService.delete(productClass)
        return if (deleted) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "Perfil no encontrado: $productClass"))
        }
    }
}
