package com.dscorp.wispadmin.routeros

import com.dscorp.wispadmin.routeros.adapter.RouterOsRestPathMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RouterOsRestPathMapperTest {

    @Test
    fun `print path appends print under rest prefix`() {
        assertEquals("/rest/system/resource/print", RouterOsRestPathMapper.printPath("/system/resource"))
        assertEquals("/rest/system/identity/print", RouterOsRestPathMapper.printPath("system/identity"))
    }

    @Test
    fun `print path keeps trailing print once`() {
        assertEquals("/rest/system/resource/print", RouterOsRestPathMapper.printPath("/system/resource/print"))
    }

    @Test
    fun `mutation path strips print and builds rest resource path`() {
        assertEquals("/rest/ip/address", RouterOsRestPathMapper.resourcePath("/ip/address"))
        assertEquals("/rest/ip/address/*1", RouterOsRestPathMapper.resourcePath("/ip/address", "*1"))
    }
}
