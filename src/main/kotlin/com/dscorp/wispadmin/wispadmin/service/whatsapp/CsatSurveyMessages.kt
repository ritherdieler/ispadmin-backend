package com.dscorp.wispadmin.wispadmin.service.whatsapp

/**
 * Copy and interactive-list labels for CSAT WhatsApp surveys.
 * Kept separate from orchestration so payload content stays easy to test and change.
 */
object CsatSurveyMessages {

    const val BUTTON_TEXT = "Calificar 📊"
    const val SECTION_TITLE = "Nivel de Satisfacción"

    const val COMMENT_PROMPT =
        "¡Muchas gracias por tu calificación! 🌟 💬 Para ayudarnos a mejorar cada día, ¿podrías dejarnos un breve comentario sobre tu experiencia?"

    const val FINAL_THANKS =
        "¡Excelente! 🙌 Agradecemos mucho tus comentarios. ¡Que tengas un maravilloso día! 🚀✨"

    fun interactiveBody(ticketId: Int): String =
        "¡Hola! 👋 Gracias por contactarnos. Por favor, califica la atención brindada en tu ticket #$ticketId 🛠️"

    fun scoreTitle(score: Int): String = when (score) {
        1 -> "1 ⭐"
        2 -> "2 ⭐⭐"
        3 -> "3 ⭐⭐⭐"
        4 -> "4 ⭐⭐⭐⭐"
        5 -> "5 ⭐⭐⭐⭐⭐"
        else -> "$score"
    }

    fun scoreDescription(score: Int): String = when (score) {
        1 -> "😡 Muy mala"
        2 -> "🙁 Mala"
        3 -> "😐 Regular"
        4 -> "🙂 Buena"
        5 -> "🤩 Excelente"
        else -> ""
    }
}
