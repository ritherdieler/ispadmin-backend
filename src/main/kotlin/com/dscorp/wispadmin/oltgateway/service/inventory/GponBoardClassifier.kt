package com.dscorp.wispadmin.oltgateway.service.inventory

object GponBoardClassifier {

    private val gponTokens = listOf("GPFD", "GPBD", "GPHF", "GPSF", "GPBH", "GPLC")

    fun isGponBoard(boardName: String, status: String): Boolean {
        val name = boardName.uppercase()
        val st = status.uppercase()
        if (name.isBlank()) return false
        if (name.contains("MCUD") || name.contains("ETH") || name.contains("X2CS")) return false
        if (st.contains("FAILED") || st.contains("ABSENT") || st == "-") return false
        return gponTokens.any { name.contains(it) } &&
            (st.contains("NORMAL") || st.contains("ONLINE") || st.isBlank())
    }

    fun defaultPortCount(boardName: String, fallback: Int): Int {
        val name = boardName.uppercase()
        return when {
            name.contains("GPFD") -> 16
            name.contains("GPHF") -> 16
            name.contains("GPBD") -> 8
            else -> fallback
        }
    }
}
