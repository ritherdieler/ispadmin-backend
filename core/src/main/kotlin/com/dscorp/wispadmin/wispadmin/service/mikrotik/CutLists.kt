package com.dscorp.wispadmin.wispadmin.service.mikrotik

data class CutList(val name: String, val dropComment: String)

object CutLists {
    val DEBTORS = CutList("deudores", DebtorCutRulePlacement.DROP_COMMENT)
    val CANCELLED = CutList("cancelados", "CORTADO POR CANCELACION - LISTA DE CANCELADOS")

    val ALL = listOf(DEBTORS, CANCELLED)
}
