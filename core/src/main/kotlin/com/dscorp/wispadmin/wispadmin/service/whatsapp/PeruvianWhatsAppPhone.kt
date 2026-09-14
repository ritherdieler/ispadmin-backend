package com.dscorp.wispadmin.wispadmin.service.whatsapp

object PeruvianWhatsAppPhone {

    fun digitsOnly(phone: String): String = phone.filter { it.isDigit() }

    fun toInternational(phone: String): String {
        val digits = digitsOnly(phone)
        val normalized = when {
            digits.length == 9 && digits.startsWith("9") -> "51$digits"
            digits.length == 11 && digits.startsWith("51") -> digits
            else -> throw IllegalArgumentException("El telefono debe ser un celular peruano valido.")
        }
        if (!normalized.substring(2).startsWith("9")) {
            throw IllegalArgumentException("El telefono debe ser un celular peruano valido.")
        }
        return normalized
    }

    fun canonicalStoragePhone(phone: String): String =
        runCatching { toInternational(phone) }.getOrElse { digitsOnly(phone) }

    fun toLocalNine(phone: String): String? {
        val digits = digitsOnly(phone)
        return when {
            digits.length == 11 && digits.startsWith("51") && digits.substring(2).startsWith("9") ->
                digits.substring(2)
            digits.length == 9 && digits.startsWith("9") -> digits
            else -> null
        }
    }

    fun queryVariants(phone: String): List<String> {
        val international = runCatching { toInternational(phone) }.getOrNull()
        val local = toLocalNine(phone)
        return listOfNotNull(international, local).distinct().ifEmpty { listOf(digitsOnly(phone)) }
    }

    fun equivalent(a: String, b: String): Boolean {
        val variantsA = queryVariants(a)
        return queryVariants(b).any { it in variantsA }
    }

    fun canonicalConversationKey(phone: String): String = canonicalStoragePhone(phone)
}
