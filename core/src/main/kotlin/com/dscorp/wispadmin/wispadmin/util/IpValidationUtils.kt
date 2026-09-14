package com.dscorp.wispadmin.wispadmin.util

fun String?.isValidIpAddress(): Boolean {
    if (this == null) return false
    val parts = this.split(".")
    if (parts.size != 4) {
        return false
    }
    for (part in parts) {
        try {
            val number = part.toInt()
            if (number < 0 || number > 255) {
                return false
            }
        } catch (e: NumberFormatException) {
            return false
        }
    }
    return true
}



