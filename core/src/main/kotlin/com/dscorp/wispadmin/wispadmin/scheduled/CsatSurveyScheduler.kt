package com.dscorp.wispadmin.wispadmin.scheduled

import com.dscorp.wispadmin.wispadmin.service.whatsapp.CsatSurveyService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CsatSurveyScheduler(
    private val csatSurveyService: CsatSurveyService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${crm.csat.scheduler-delay-ms:60000}", initialDelayString = "\${crm.csat.scheduler-initial-delay-ms:45000}")
    fun processDue() {
        try {
            csatSurveyService.processDueSurveys()
        } catch (e: Exception) {
            log.warn("CSAT scheduler failed: {}", e.message)
        }
    }
}
