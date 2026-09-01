package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ApplicationProdDatasourceHostTest {

    @Test
    fun prod_jdbc_targets_docker_mysql_schema_ispadmin() {
        val root = Path.of(System.getProperty("user.dir"))
        val prod = Files.readString(root.resolve("src/main/resources/application-prod.properties"))
        assertTrue(
            prod.contains("jdbc:mysql://mysql:3306/ispadmin?"),
            prod.lineSequence().filter { it.contains("datasource.url") }.joinToString("\n")
        )
    }
}
