package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.IpPoolDto
import com.dscorp.wispadmin.wispadmin.repository.IpPoolRepository
import com.dscorp.wispadmin.wispadmin.requestbody.IpPoolRequest
import com.dscorp.wispadmin.wispadmin.service.IpPoolService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/ip-pool")
class IpPoolController(
    private val service: IpPoolService,
    private val repository: IpPoolRepository
) {

    @PostMapping
    fun registerIpPool(@RequestBody newIpPool: IpPoolRequest): ResponseEntity<IpPoolDto> =
        ResponseEntity.ok(service.registerIpPool(newIpPool)?.toDto())

    @GetMapping
    fun getIpPool(): ResponseEntity<List<IpPoolDto>> =
        ResponseEntity.ok(repository.findAll().sortedByDescending { it.createdAt }.map { it.toDto() })

    @GetMapping("get-ips")
    fun getIps(@RequestParam ipPoolId: Int): ResponseEntity<List<String>> =
        ResponseEntity.ok(repository.getIps(ipPoolId))

    @DeleteMapping("/{id}")
    fun deleteIpPool(@PathVariable id: Int): ResponseEntity<String> {
        service.deleteIpPool(id)
        return ResponseEntity.ok("IP Pool eliminado correctamente")
    }

    @PutMapping("/{id}/activate")
    fun activateIpPool(@PathVariable id: Int): ResponseEntity<IpPoolDto> =
        ResponseEntity.ok(service.activateIpPool(id).toDto())

    @PutMapping("/{id}/deactivate")
    fun deactivateIpPool(@PathVariable id: Int): ResponseEntity<IpPoolDto> =
        ResponseEntity.ok(service.deactivateIpPool(id).toDto())
}
