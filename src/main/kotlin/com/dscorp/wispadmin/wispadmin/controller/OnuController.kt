package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.data.model.util.BaseResponse
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.repository.MufaRepository
import com.dscorp.wispadmin.wispadmin.response.Response
import com.dscorp.wispadmin.wispadmin.service.OnuService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/onu")
class OnuController @Autowired constructor(
    private val onuService: OnuService,
    private val mufaRepository: MufaRepository,
    private val errorLogRepository: ErrorLogRepository
){

    //    val objectErrorResponse: ResponseEntity<OnuDto> = ResponseEntity.status(500).body(null)
    val listObjectErrorResponse: ResponseEntity<List<Response>> = ResponseEntity.status(500).body(null)


    @DeleteMapping
    fun deleteOnu(@RequestParam onuExternalId: String): BaseResponse {
        return try {
            onuService.deleteOnu(onuExternalId)
            BaseResponse(
                status = 200,
                message = "Onu eliminada correctamente",
                data = "",
                error = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.ONU))
            BaseResponse(
                status = 500,
                data = null,
                message = "Error",
                error = e.message
            )
        }
    }

    @GetMapping("unconfigured_onus")
    fun getUnConfiguredOnus(): ResponseEntity<List<Response>> {
        return try {
            val onuList = onuService.getUnConfiguredOnus()
            ResponseEntity.status(200).body(onuList)
        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.ONU))
            listObjectErrorResponse
        }
    }


    @GetMapping("getBySn")
    fun getOnuBySn(@RequestParam onuSn: String): BaseResponse {
        return try {
            val onu = onuService.getOnuBySn(onuSn)
            if (onu.onus.isNotEmpty())
                BaseResponse(
                    status = 200,
                    data = onu,
                    message = "Success",
                    error = null
                )
            else
                BaseResponse(
                    status = 500,
                    data = null,
                    message = "Error",
                    error = "No se encontró la ONU registrada en la OLT"
                )

        } catch (e: Exception) {
            e.printStackTrace()
            errorLogRepository.save(e.toErrorLog(Modules.ONU))
            BaseResponse(
                status = 500,
                data = null,
                message = "Error",
                error = e.message
            )
        }
    }

}
