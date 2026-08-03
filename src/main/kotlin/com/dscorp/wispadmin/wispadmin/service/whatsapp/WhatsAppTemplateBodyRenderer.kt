package com.dscorp.wispadmin.wispadmin.service.whatsapp

class WhatsAppTemplateBodyRenderer {

    private val legacyPreviewRegex =
        Regex("""^([a-z0-9_]+)\s+\[(.+)]$""", RegexOption.IGNORE_CASE)

    fun render(bodyTemplate: String, parameters: List<NamedTemplateParameter>): String {
        var result = bodyTemplate
        for (param in parameters) {
            val placeholder = "{{${param.parameterName}}}"
            result = result.replace(placeholder, param.text)
        }
        return result
    }

    fun parseLegacyPreview(stored: String): Pair<String, Map<String, String>>? {
        val trimmed = stored.trim()
        val match = legacyPreviewRegex.matchEntire(trimmed) ?: return null
        val metaName = match.groupValues[1]
        val paramsSection = match.groupValues[2]
        if (paramsSection.isBlank()) return metaName to emptyMap()
        val params = linkedMapOf<String, String>()
        paramsSection.split(", ").forEach { segment ->
            val eqIndex = segment.indexOf('=')
            if (eqIndex <= 0) return@forEach
            val key = segment.substring(0, eqIndex).trim()
            val value = segment.substring(eqIndex + 1).trim()
            if (key.isNotEmpty()) params[key] = value
        }
        if (params.isEmpty()) return null
        return metaName to params
    }

    fun buildPreviewFromDefinition(
        definition: WhatsAppTemplateDefinition,
        parameters: List<NamedTemplateParameter>,
        bodyText: String
    ): String {
        if (bodyText.isBlank()) {
            throw IllegalStateException(
                "La plantilla ${definition.metaName} no tiene texto BODY sincronizado desde Meta. " +
                    "Ejecute la sincronizacion de plantillas WhatsApp."
            )
        }
        return render(bodyText, parameters)
    }

    fun buildDisplayMessage(
        storedMessage: String?,
        messageType: String?,
        bodyTextForMetaName: (String) -> String?
    ): String? {
        if (storedMessage.isNullOrBlank()) return storedMessage
        val legacy = parseLegacyPreview(storedMessage)
        if (legacy != null) {
            val (metaName, paramMap) = legacy
            val parameters = paramMap.map { (name, value) -> NamedTemplateParameter(name, value) }
            val bodyText = bodyTextForMetaName(metaName)
            if (!bodyText.isNullOrBlank()) {
                return render(bodyText, parameters)
            }
            val definition = WhatsAppTemplateHumanFallback.findDefinitionByMetaName(metaName)
            if (definition != null) {
                return WhatsAppTemplateHumanFallback.render(definition, parameters)
            }
            return storedMessage
        }
        if (isCatalogTemplateMessageType(messageType)) {
            return storedMessage
        }
        return storedMessage
    }

    fun legacyPreviewText(
        definition: WhatsAppTemplateDefinition,
        parameters: List<NamedTemplateParameter>
    ): String {
        val paramsText = parameters.joinToString(", ") { "${it.parameterName}=${it.text}" }
        return "${definition.metaName} [$paramsText]"
    }

    private fun isCatalogTemplateMessageType(messageType: String?): Boolean {
        if (messageType.isNullOrBlank()) return false
        return runCatching { WhatsAppTemplateCatalog.getByCodeString(messageType) }.isSuccess
    }
}
