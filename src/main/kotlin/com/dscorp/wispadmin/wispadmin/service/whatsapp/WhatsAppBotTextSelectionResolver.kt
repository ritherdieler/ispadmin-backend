package com.dscorp.wispadmin.wispadmin.service.whatsapp

object WhatsAppBotTextSelectionResolver {

    fun resolve(messageText: String?, options: List<WhatsAppInteractiveOption>): String? {
        val normalized = semanticText(messageText) ?: return null
        val index = when (normalized) {
            "1", "a" -> 0
            "2", "b" -> 1
            "3", "c" -> 2
            else -> null
        }
        if (index != null) return options.getOrNull(index)?.id

        val matches = options.filter { option ->
            val title = semanticText(option.title) ?: return@filter false
            normalized == title ||
                normalized.contains(title) ||
                title.split(" ").filter { it.length > 2 }.all { normalized.containsWord(it) }
        }
        return matches.singleOrNull()?.id
    }

    private fun semanticText(value: String?): String? {
        return WhatsAppInboundIntentRouter.normalize(value)
            ?.replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.ifBlank { null }
    }

    private fun String.containsWord(word: String): Boolean =
        Regex("""(?:^|\s)${Regex.escape(word)}(?:\s|$)""").containsMatchIn(this)
}
