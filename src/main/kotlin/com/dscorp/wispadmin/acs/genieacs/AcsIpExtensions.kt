package com.dscorp.wispadmin.acs.genieacs

fun String.getBaseIpFromRange(): String {
    val segment = this.split("/")
    val a = segment[0].split(".")
    val b = a.subList(0, a.size - 1)
    return b.reduce { acc, s -> "$acc.$s" } + "."
}
