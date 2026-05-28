package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.DownloadDocumentDto
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.extensions.getFirstDayOfMonthInMillis
import com.dscorp.wispadmin.wispadmin.extensions.getLastDayOfMonthInMillis
import com.dscorp.wispadmin.wispadmin.service.ReportService
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.ByteArrayOutputStream
import java.util.*


@RestController
@RequestMapping("/report")
class ReportController {


    @Autowired
    lateinit var reportService: ReportService

    @GetMapping("/canceled-current-month")
    fun getCancelledSubscriptionFromLastMonth(): ResponseEntity<DownloadDocumentDto> {
        return try {
            val firstDayOfMonthInMillis =
                Calendar.getInstance().getFirstDayOfMonthInMillis()
            val lastDayOfMonthInMillis =
                Calendar.getInstance().getLastDayOfMonthInMillis()
            val result =
                reportService.getCancelledSubscriptionsBetweenTwoDates(firstDayOfMonthInMillis, lastDayOfMonthInMillis)
                    .map { it.toDto() }

            val response = createGenericSubscriptionDocument(result, "suscripciones_canceladas_mes_actual")
            ResponseEntity.ok().body(response)
        } catch (e: Exception) {
            e.printStackTrace()
            return ResponseEntity.status(500).body(null)
        }
    }

    @GetMapping("/canceled-past-month")
    fun getCancelledSubscriptionsFromPastMont(): ResponseEntity<DownloadDocumentDto> {
        return try {
            val firstDayOfMonthInMillis =
                Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.getFirstDayOfMonthInMillis()
            val lastDayOfMonthInMillis =
                Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.getLastDayOfMonthInMillis()
            val result =
                reportService.getCancelledSubscriptionsBetweenTwoDates(firstDayOfMonthInMillis, lastDayOfMonthInMillis)
                    .map { it.toDto() }

            val response = createGenericSubscriptionDocument(result, "suscripciones_canceladas_mes_pasado")
            ResponseEntity.ok().body(response)
        } catch (e: Exception) {
            e.printStackTrace()
            return ResponseEntity.status(500).body(null)
        }
    }
}


private fun createGenericSubscriptionDocument(
    subscriptions: List<SubscriptionDto>,
    documentName: String
): DownloadDocumentDto {
    // Crear el archivo de Excel utilizando Apache POI
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet(documentName)

    // Crear la primera fila con los encabezados
    val headerRow = sheet.createRow(0)
    headerRow.createCell(0).setCellValue("DNI")
    headerRow.createCell(1).setCellValue("Nombres")
    headerRow.createCell(2).setCellValue("Apellidos")
    headerRow.createCell(3).setCellValue("Telefono")
    headerRow.createCell(4).setCellValue("Direccion")

    // Llenar las filas restantes con los datos de los pagos
    var rowNum = 1
    for (subscription in subscriptions) {
        val row = sheet.createRow(rowNum++)
        row.createCell(0).setCellValue(subscription.dni)
        row.createCell(1).setCellValue(subscription.firstName)
        row.createCell(2).setCellValue(subscription.lastName)
        row.createCell(3).setCellValue(subscription.phone)
        row.createCell(4).setCellValue("${subscription.place?.name} - ${subscription.address}")
    }

    // Guardar el libro de Excel en un objeto ByteArrayOutputStream
    val stream = ByteArrayOutputStream()
    workbook.write(stream)

    // Crear la respuesta HTTP con los datos del archivo de Excel
    val bytes = stream.toByteArray()

    val bytesToBase64 = Base64.getEncoder().encodeToString(bytes)


    return DownloadDocumentDto(name = documentName, type = "xlsx", base64 = bytesToBase64)
}
