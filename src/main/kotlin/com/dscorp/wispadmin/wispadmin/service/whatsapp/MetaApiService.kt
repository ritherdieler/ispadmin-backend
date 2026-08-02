package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.service.WhatsAppSendResult
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

@Service
class MetaApiService(
    private val whatsAppService: WhatsAppService
) {

    suspend fun sendReplyButtons(
        phoneNumber: String,
        bodyText: String,
        buttons: List<WhatsAppService.InteractiveButtonOption>
    ): WhatsAppSendResult = withContext(Dispatchers.IO) {
        whatsAppService.sendInteractiveReplyButtons(phoneNumber, bodyText, buttons)
    }

    suspend fun sendListMessage(
        phoneNumber: String,
        bodyText: String,
        buttonText: String,
        sectionTitle: String,
        rows: List<WhatsAppService.InteractiveListOption>
    ): WhatsAppSendResult = withContext(Dispatchers.IO) {
        whatsAppService.sendInteractiveListMessage(phoneNumber, bodyText, buttonText, sectionTitle, rows)
    }

    suspend fun passThreadControl(
        userWaId: String,
        targetAppId: String,
        metadata: String
    ): WhatsAppSendResult = withContext(Dispatchers.IO) {
        whatsAppService.passThreadControl(userWaId, targetAppId, metadata)
    }

    suspend fun takeThreadControl(
        userWaId: String,
        metadata: String
    ): WhatsAppSendResult = withContext(Dispatchers.IO) {
        whatsAppService.takeThreadControl(userWaId, metadata)
    }
}
