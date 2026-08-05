package com.dscorp.wispadmin.wispadmin.service.whatsapp

data class WhatsAppInteractiveOption(
    val id: String,
    val title: String
)

data class WhatsAppInteractiveListOption(
    val id: String,
    val title: String,
    val description: String? = null
)

data class WhatsAppInteractiveSection(
    val title: String,
    val rows: List<WhatsAppInteractiveListOption>
)

object WhatsAppInteractiveMessageValidator {

    fun validateReplyButtons(
        bodyText: String,
        footerText: String?,
        buttons: List<WhatsAppInteractiveOption>
    ) {
        requireLength(bodyText, 1, 1024, "bodyText")
        requireOptionalMaxLength(footerText, 60, "footerText")
        require(buttons.size in 1..3) { "Reply buttons debe tener entre 1 y 3 opciones" }
        buttons.forEach {
            requireLength(it.id, 1, 256, "button.id")
            requireLength(it.title, 1, 20, "button.title")
        }
        requireUnique(buttons.map { it.id }, "button.id")
        requireUnique(buttons.map { it.title }, "button.title")
    }

    fun validateList(
        bodyText: String,
        buttonText: String,
        footerText: String?,
        sections: List<WhatsAppInteractiveSection>
    ) {
        requireLength(bodyText, 1, 4096, "bodyText")
        requireLength(buttonText, 1, 20, "buttonText")
        requireOptionalMaxLength(footerText, 60, "footerText")
        require(sections.size in 1..10) { "List message debe tener entre 1 y 10 secciones" }
        sections.forEach { section ->
            requireLength(section.title, 1, 24, "section.title")
            require(section.rows.isNotEmpty()) { "Cada sección debe tener al menos una opción" }
            section.rows.forEach { row ->
                requireLength(row.id, 1, 200, "row.id")
                requireLength(row.title, 1, 24, "row.title")
                requireOptionalMaxLength(row.description, 72, "row.description")
            }
        }
        val rows = sections.flatMap { it.rows }
        require(rows.size in 1..10) { "List message debe tener entre 1 y 10 opciones totales" }
        requireUnique(rows.map { it.id }, "row.id")
    }

    private fun requireLength(value: String, min: Int, max: Int, field: String) {
        val length = value.trim().codePointLength()
        require(length in min..max) { "$field debe tener entre $min y $max caracteres" }
    }

    private fun requireOptionalMaxLength(value: String?, max: Int, field: String) {
        if (value == null) return
        require(value.trim().codePointLength() <= max) { "$field debe tener como máximo $max caracteres" }
    }

    private fun requireUnique(values: List<String>, field: String) {
        require(values.map { it.trim() }.distinct().size == values.size) { "$field debe ser único" }
    }

    private fun String.codePointLength(): Int = codePointCount(0, length)
}
