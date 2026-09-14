package com.dscorp.wispadmin.wispadmin.scripts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class Tr069E2eFirebaseDeleteTest {

    private fun root(): Path = Path.of(System.getProperty("user.dir"))

    @Test
    fun helper_resolves_ca_bundle_and_builds_ssl_context() {
        val helper = root().resolve("scripts/tr069_e2e_firebase_delete.py")
        assertTrue(Files.exists(helper), "missing $helper")
        val text = Files.readString(helper)
        assertTrue(text.contains("certifi"), text)
        assertTrue(text.contains("resolve_cafile"), text)
        assertTrue(text.contains("build_ssl_context"), text)
        val pb = ProcessBuilder("python3", helper.toString(), "--check-ssl")
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.waitFor(), out)
        assertTrue(out.contains("SSL_OK"), out)
    }

    @Test
    fun cleanup_script_uses_helper_and_continues_mysql_if_firebase_fails() {
        val script = Files.readString(root().resolve("scripts/tr069-e2e-hard-cleanup.sh"))
        assertTrue(script.contains("tr069_e2e_firebase_delete.py"), script)
        assertTrue(script.contains("FIREBASE_EXIT"), script)
        val firebaseIdx = script.indexOf("tr069_e2e_firebase_delete.py")
        val mysqlIdx = script.indexOf("== MySQL delete")
        assertTrue(firebaseIdx >= 0 && mysqlIdx > firebaseIdx, "MySQL must run after Firebase block")
        assertTrue(script.contains("set +e"), script)
    }
}
