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

    @Test
    fun prod_face_models_read_from_host_filesystem() {
        val root = Path.of(System.getProperty("user.dir"))
        val prod = Files.readString(root.resolve("src/main/resources/application-prod.properties"))
        val dev = Files.readString(root.resolve("src/main/resources/application-dev.properties"))
        assertTrue(prod.contains("face.login.djl-model-path=/opt/gigafiber/models/face_feature.zip"), prod)
        assertTrue(prod.contains("face.login.djl-detector-model-path=/opt/gigafiber/models/ultranet.zip"), prod)
        assertTrue(prod.contains("face.embedding.model-path=/opt/gigafiber/models/arcface_w600k_mbf.onnx"), prod)
        assertTrue(dev.contains("face.login.djl-model-path=classpath:models/face_feature.zip"), dev)
        assertTrue(dev.contains("face.login.djl-detector-model-path=classpath:models/ultranet.zip"), dev)
        assertTrue(dev.contains("face.embedding.model-path=classpath:models/arcface_w600k_mbf.onnx"), dev)
    }
}
