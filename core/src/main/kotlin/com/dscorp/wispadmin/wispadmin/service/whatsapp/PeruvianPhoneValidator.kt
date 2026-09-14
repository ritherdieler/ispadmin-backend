package com.dscorp.wispadmin.wispadmin.service.whatsapp

object PeruvianPhoneValidator {
    fun isValid(phone: String): Boolean {
        val digits = phone.filter { it.isDigit() }
        return (digits.length == 9 && digits.startsWith("9")) ||
            (digits.length == 11 && digits.startsWith("519"))
    }
}
