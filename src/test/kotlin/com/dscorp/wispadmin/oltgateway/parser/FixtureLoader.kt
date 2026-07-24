package com.dscorp.wispadmin.oltgateway.parser

object FixtureLoader {
    fun load(name: String): String {
        val stream = javaClass.classLoader.getResourceAsStream("oltgateway/fixtures/$name")
            ?: error("Fixture not found: oltgateway/fixtures/$name")
        return stream.bufferedReader().use { it.readText() }
    }
}
