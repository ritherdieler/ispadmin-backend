package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.IpPoolDto
import com.dscorp.wispadmin.wispadmin.repository.IpPoolRepository
import com.dscorp.wispadmin.wispadmin.requestbody.IpPoolRequest
import com.dscorp.wispadmin.wispadmin.service.IpPoolService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/ip-pool")
class IpPoolController {

    val objectErrorResponse: ResponseEntity<IpPoolDto> = ResponseEntity.status(500).body(null)
    val listObjectErrorResponse: ResponseEntity<List<IpPoolDto>> = ResponseEntity.status(500).body(null)

    @Autowired
    lateinit var service: IpPoolService

    @Autowired
    lateinit var repository: IpPoolRepository

    @PostMapping
    fun registerIpPool(@RequestBody newIpPool: IpPoolRequest): ResponseEntity<IpPoolDto> {
        return try {
            val ipPool = service.registerIpPool(newIpPool)
            ResponseEntity.status(200).body(ipPool?.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            objectErrorResponse
        }
    }

    @GetMapping
    fun getIpPool(): ResponseEntity<List<IpPoolDto>> {
        return try {
            val ipPoolList = repository.findAll().sortedByDescending { it.createdAt }
            ResponseEntity.status(200).body(ipPoolList.map { it.toDto() })
        } catch (e: Exception) {
            e.printStackTrace()
            listObjectErrorResponse
        }
    }

    @GetMapping("get-ips")
    fun getIps(@RequestParam ipPoolId :Int): ResponseEntity<List<String>> {
        return try {
            val ipPoolList = repository.getIps(ipPoolId)
            ResponseEntity.status(200).body(ipPoolList)
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @DeleteMapping("/{id}")
    fun deleteIpPool(@PathVariable id: Int): ResponseEntity<String> {
        return try {
            service.deleteIpPool(id)
            ResponseEntity.status(200).body("IP Pool eliminado correctamente")
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body("Error al eliminar el IP Pool: ${e.message}")
        }
    }

    @PutMapping("/{id}/activate")
    fun activateIpPool(@PathVariable id: Int): ResponseEntity<IpPoolDto> {
        return try {
            val activatedIpPool = service.activateIpPool(id)
            ResponseEntity.status(200).body(activatedIpPool.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

    @PutMapping("/{id}/deactivate")
    fun deactivateIpPool(@PathVariable id: Int): ResponseEntity<IpPoolDto> {
        return try {
            val deactivatedIpPool = service.deactivateIpPool(id)
            ResponseEntity.status(200).body(deactivatedIpPool.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body(null)
        }
    }

}
