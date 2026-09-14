package com.dscorp.wispadmin.wispadmin.service.mikrotik

object DebtorCutRulePlacement {
    const val DROP_COMMENT = "CORTADO POR DEUDA - LISTA DE DEUDORES"

    fun placeBeforeId(rules: List<Map<String, String>>): String? {
        return rules.firstOrNull { isBlanketSubscriberAccept(it) }?.get(".id")
    }

    private fun isBlanketSubscriberAccept(rule: Map<String, String>): Boolean {
        if (rule[".id"].isNullOrBlank()) return false
        if (chainOf(rule) != "forward") return false
        if (rule["action"] != "accept") return false
        if (rule["comment"] == DROP_COMMENT) return false
        if (filled(rule["src-address"]) || filled(rule["dst-address"])) return false
        if (filled(rule["src-address-list"]) || filled(rule["dst-address-list"])) return false
        if (filled(rule["dst-port"])) return false
        return filled(rule["in-interface"]) || filled(rule["in-interface-list"])
    }

    private fun chainOf(rule: Map<String, String>): String {
        val chain = rule["chain"]
        return if (chain.isNullOrBlank()) "forward" else chain
    }

    private fun filled(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return value != "-"
    }
}
