package com.dscorp.wispadmin.routeros

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RouterOsEntryIdTest {

    @Test
    fun `normalize adds asterisk when missing`() {
        assertEquals("*190580", RouterOsEntryId.normalize("190580"))
    }

    @Test
    fun `normalize keeps existing asterisk`() {
        assertEquals("*190580", RouterOsEntryId.normalize("*190580"))
    }

    @Test
    fun `normalize trims whitespace`() {
        assertEquals("*190580", RouterOsEntryId.normalize(" 190580 "))
    }
}
