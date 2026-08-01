package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.AttributeConverter
import javax.persistence.Convert
import javax.persistence.Converter
import javax.persistence.Entity
import javax.persistence.Enumerated
import javax.persistence.EnumType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

enum class WhatsAppChatStatus {
    BOT_ACTIVE,
    ESPERANDO_ASESOR
}

enum class WhatsAppConversationStep {
    MAIN_MENU,
    SUPPORT_MENU,
    SUPPORT_DIAG,
    DEBT_VIEW,
    ESPERANDO_ASESOR
}

@Converter
class WhatsAppConversationStepConverter : AttributeConverter<WhatsAppConversationStep, String> {
    override fun convertToDatabaseColumn(attribute: WhatsAppConversationStep?): String {
        return attribute?.name ?: WhatsAppConversationStep.MAIN_MENU.name
    }

    override fun convertToEntityAttribute(dbData: String?): WhatsAppConversationStep {
        val value = dbData?.trim().orEmpty()
        return WhatsAppConversationStep.values().firstOrNull { it.name == value }
            ?: WhatsAppConversationStep.MAIN_MENU
    }
}

@Entity
@Table(
    name = "whatsapp_chat_state",
    indexes = [
        Index(name = "idx_wachatstate_phone", columnList = "phone", unique = true),
        Index(name = "idx_wachatstate_status", columnList = "status"),
        Index(name = "idx_wachatstate_current_step", columnList = "current_step")
    ]
)
data class WhatsAppChatState(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(nullable = false, unique = true, length = 32)
    var phone: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    var status: WhatsAppChatStatus = WhatsAppChatStatus.BOT_ACTIVE,

    @Convert(converter = WhatsAppConversationStepConverter::class)
    @Column(name = "current_step", nullable = false, length = 64, columnDefinition = "varchar(64) default 'MAIN_MENU'")
    var currentStep: WhatsAppConversationStep = WhatsAppConversationStep.MAIN_MENU,

    @Column(nullable = false)
    var botPaused: Boolean = false,

    @Column(length = 500)
    var metadata: String? = null,

    var lastInteractionAt: LocalDateTime? = null,

    var updatedAt: LocalDateTime = LocalDateTime.now(),

    var createdAt: LocalDateTime = LocalDateTime.now()
)
