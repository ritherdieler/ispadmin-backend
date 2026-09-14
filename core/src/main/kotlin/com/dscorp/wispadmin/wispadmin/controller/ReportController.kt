package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.DownloadDocumentDto
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import com.dscorp.wispadmin.wispadmin.service.ReportService
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.ByteArrayOutputStream
import java.util.*

@RestController
@RequestMapping("/report")
class ReportController(
    private val reportService: ReportService
) {

    private fun monthRange(monthsAgo: Long): Pair<LocalDateTime, LocalDateTime> {
        val zone = ZoneId.of("America/Lima")
        val targetMonth = LocalDate.now(zone).minusMonths(monthsAgo)

        val startDate = targetMonth.withDayOfMonth(1).atStartOfDay()
        val endDate = targetMonth.plusMonths(1).withDayOfMonth(1).atStartOfDay()

        return startDate to endDate
    }

    @GetMapping("/canceled-current-month")
    fun getCancelledSubscriptionFromLastMonth(): ResponseEntity<DownloadDocumentDto> {
        val (startDate, endDate) = monthRange(monthsAgo = 0)
        val result = reportService.getCancelledSubscriptionsBetweenTwoDates(startDate, endDate)
            .map { it.toDto() }

        val response = createGenericSubscriptionDocument(result, "suscripciones_canceladas_mes_actual")
        return ResponseEntity.ok().body(response)
    }

    @GetMapping("/canceled-past-month")
    fun getCancelledSubscriptionsFromPastMont(): ResponseEntity<DownloadDocumentDto> {
        val (startDate, endDate) = monthRange(monthsAgo = 1)
        val result = reportService.getCancelledSubscriptionsBetweenTwoDates(startDate, endDate)
            .map { it.toDto() }

        val response = createGenericSubscriptionDocument(result, "suscripciones_canceladas_mes_pasado")
        return ResponseEntity.ok().body(response)
    }
}


private fun createGenericSubscriptionDocument(
    subscriptions: List<SubscriptionDto>,
    documentName: String
): DownloadDocumentDto {
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet(documentName)

    val headerRow = sheet.createRow(0)
    headerRow.createCell(0).setCellValue("DNI")
    headerRow.createCell(1).setCellValue("Nombres")
    headerRow.createCell(2).setCellValue("Apellidos")
    headerRow.createCell(3).setCellValue("Telefono")
    headerRow.createCell(4).setCellValue("Direccion")

    var rowNum = 1
    for (subscription in subscriptions) {
        val row = sheet.createRow(rowNum++)
        row.createCell(0).setCellValue(subscription.dni)
        row.createCell(1).setCellValue(subscription.firstName)
        row.createCell(2).setCellValue(subscription.lastName)
        row.createCell(3).setCellValue(subscription.phone)
        row.createCell(4).setCellValue("${subscription.place?.name} - ${subscription.address}")
    }

    val stream = ByteArrayOutputStream()
    workbook.write(stream)

    val bytes = stream.toByteArray()

    val bytesToBase64 = Base64.getEncoder().encodeToString(bytes)

    return DownloadDocumentDto(name = documentName, type = "xlsx", base64 = bytesToBase64)
}
