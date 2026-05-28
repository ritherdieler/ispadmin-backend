package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.data.model.Outlay
import com.dscorp.wispadmin.wispadmin.dto.OutlayDTO
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.repository.OutlayRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.service.FirebaseStorageService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/outLay")
class OutLayController @Autowired constructor(
    private val outlayRepository: OutlayRepository,
    private val userRepository: UserRepository,
    private val errorLogRepository: ErrorLogRepository,
    private val storageService: FirebaseStorageService
) {

    val objectErrorResponse: ResponseEntity<Outlay> = ResponseEntity.status(500).body(null)
    val listObjectErrorResponse: ResponseEntity<List<Outlay>> = ResponseEntity.status(500).body(null)

    @GetMapping
    fun getAllOutlays(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(defaultValue = "false") currentMonthOnly: Boolean
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("date").descending())
            
            val outlays = if (currentMonthOnly) {
                val calendar = java.util.Calendar.getInstance()
                val firstDayOfMonth = calendar.apply {
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.time
                
                val lastDayOfMonth = calendar.apply {
                    set(java.util.Calendar.DAY_OF_MONTH, getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
                    set(java.util.Calendar.HOUR_OF_DAY, 23)
                    set(java.util.Calendar.MINUTE, 59)
                    set(java.util.Calendar.SECOND, 59)
                    set(java.util.Calendar.MILLISECOND, 999)
                }.time
                
                outlayRepository.findByDateBetween(firstDayOfMonth, lastDayOfMonth, pageable)
            } else {
                outlayRepository.findAll(pageable)
            }
            
            val response = mapOf(
                "content" to outlays.content.map { it.toDto() },
                "totalElements" to outlays.totalElements,
                "totalPages" to outlays.totalPages,
                "currentPage" to outlays.number,
                "size" to outlays.size,
                "hasNext" to outlays.hasNext(),
                "hasPrevious" to outlays.hasPrevious()
            )
            
            ResponseEntity.status(200).body(response)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.OUTLAY))
            ResponseEntity.status(500).body(null)
        }
    }

    @PostMapping
    fun createOutlay(
        @RequestParam("amount") amount: Double,
        @RequestParam("description") description: String,
        @RequestParam("document_code") documentCode: String,
        @RequestParam("category") category: String,
        @RequestParam("cost_center") costCenter: String,
        @RequestParam("userId") userId: Int,
        @RequestParam(value = "image", required = false) image: Array<MultipartFile>?
    ): ResponseEntity<OutlayDTO> {
        return try {
            println("=== OutLayController Debug ===")
            println("Amount: $amount")
            println("Description: $description")
            println("Document Code: $documentCode")
            println("Category: $category")
            println("Cost Center: $costCenter")
            println("UserId: $userId")
            println("Images count: ${image?.size ?: 0}")
            
            if (amount <= 0) {
                return ResponseEntity.badRequest().body(null)
            }
            
            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                return ResponseEntity.notFound().build()
            }
            
            // Subir imágenes a Firebase si se proporcionan
            var receiptUrls: List<String> = emptyList()
            if (image != null && image.isNotEmpty()) {
                receiptUrls = image.mapNotNull { receipt ->
                    if (!receipt.isEmpty) {
                        try {
                            val firebaseUrl = storageService.uploadFileToFolder(receipt, "outlays")
                            firebaseUrl // Usar la URL de Firebase, no el nombre del archivo
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null // Continuar sin esta imagen si falla la subida
                        }
                    } else {
                        null
                    }
                }
            }
            
            val outlay = Outlay(
                amount = amount,
                description = description,
                document_code = documentCode,
                category = category,
                cost_center = costCenter,
                receipt_url = receiptUrls.joinToString(","), // Concatenar URLs con comas
                responsible = user,
                date = java.util.Date()
            )
            
            val savedOutlay = outlayRepository.save(outlay)
            ResponseEntity.status(201).body(savedOutlay.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.OUTLAY))
            ResponseEntity.status(500).body(null)
        }
    }

    @PutMapping("/{id}")
    fun updateOutlay(@PathVariable id: Int, @RequestBody outlayDTO: OutlayDTO, @RequestParam userId: Int): ResponseEntity<OutlayDTO> {
        return try {
            if (outlayDTO.amount <= 0) {
                return ResponseEntity.badRequest().body(null)
            }
            
            val existingOutlay = outlayRepository.findById(id).orElse(null)
            if (existingOutlay == null) {
                return ResponseEntity.notFound().build()
            }
            
            val user = userRepository.findById(userId).orElse(null)
            if (user == null) {
                return ResponseEntity.notFound().build()
            }
            
            val updatedOutlay = existingOutlay.copy(
                amount = outlayDTO.amount,
                description = outlayDTO.description,
                document_code = outlayDTO.document_code,
                date = outlayDTO.date,
                category = outlayDTO.category,
                cost_center = outlayDTO.cost_center,
                responsible = user
            )
            
            val savedOutlay = outlayRepository.save(updatedOutlay)
            ResponseEntity.ok(savedOutlay.toDto())
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.OUTLAY))
            ResponseEntity.status(500).body(null)
        }
    }

    @DeleteMapping("/{id}")
    fun deleteOutlay(@PathVariable id: Int): ResponseEntity<Void> {
        return try {
            val existingOutlay = outlayRepository.findById(id).orElse(null)
            if (existingOutlay == null) {
                return ResponseEntity.status(404).build()
            }
            
            outlayRepository.delete(existingOutlay)
            ResponseEntity.status(200).build()
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.OUTLAY))
            ResponseEntity.status(500).build()
        }
    }
}
