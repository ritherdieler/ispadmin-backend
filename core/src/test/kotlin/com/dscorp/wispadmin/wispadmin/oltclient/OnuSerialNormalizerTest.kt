package com.dscorp.wispadmin.wispadmin.oltclient

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OnuSerialNormalizerTest {
    @Test
    fun `prefers parenthetical ascii sn`() {
        assertEquals(
            "ZTEGDC47BFFD",
            OnuSerialNormalizer.preferredSn("5A544547DC47BFFD (ZTEG-DC47BFFD)"),
        )
    }

    @Test
    fun `decodes 16 hex vendor sn`() {
        assertEquals("ZTEGDC47BFFD", OnuSerialNormalizer.preferredSn("5A544547DC47BFFD"))
    }

    @Test
    fun `keeps plain ascii sn`() {
        assertEquals("ZTEGDC47BFFD", OnuSerialNormalizer.preferredSn("ZTEGDC47BFFD"))
    }
}
