package com.dscorp.wispadmin.wispadmin.schema

import com.dscorp.wispadmin.wispadmin.config.SubsystemScanFilter
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy
import org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.type.filter.AnnotationTypeFilter
import java.sql.DriverManager
import javax.persistence.Entity

@Tag("clone-db")
class CoreFlywayCloneHibernateValidateTest {

    @Test
    fun hibernateValidatePassesAfterCoreFlywayOnClone() {
        val jdbc = System.getenv("CORE_FLYWAY_CLONE_JDBC")
        assumeTrue(!jdbc.isNullOrBlank(), "CORE_FLYWAY_CLONE_JDBC is required for clone validate")
        val user = System.getenv("CORE_FLYWAY_CLONE_USER") ?: "root"
        val password = System.getenv("CORE_FLYWAY_CLONE_PASSWORD") ?: ""
        val catalog = jdbcCatalog(jdbc)
        assertFalse(catalog == "ispadmin" || catalog == "ispadmin_staging", catalog)

        DriverManager.getConnection(jdbc, user, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT version, success FROM flyway_schema_history
                    WHERE version IS NOT NULL ORDER BY installed_rank
                    """.trimIndent(),
                ).use { rows ->
                    val versions = linkedMapOf<String, Int>()
                    while (rows.next()) {
                        versions[rows.getString(1)] = rows.getInt(2)
                    }
                    (39..55).filter { it != 47 }.forEach { version ->
                        assertEquals(1, versions[version.toString()], "Flyway V$version on $catalog")
                    }
                }
                statement.executeQuery(
                    """
                    SELECT COLUMN_TYPE FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 'assistance_ticket'
                      AND column_name = 'priority'
                    """.trimIndent(),
                ).use { rows ->
                    assertTrue(rows.next(), "priority missing")
                    assertTrue(rows.getString(1).startsWith("int"), rows.getString(1))
                }
            }
        }

        val environment = StandardEnvironment()
        environment.propertySources.addFirst(
            MapPropertySource(
                "clone",
                mapOf(
                    "gigafiber.subsystems.observability.enabled" to "true",
                    "gigafiber.subsystems.netdiag.enabled" to "true",
                    "gigafiber.subsystems.servicehealth.enabled" to "true",
                    "gigafiber.subsystems.acs.enabled" to "false",
                    "gigafiber.subsystems.oltgateway.enabled" to "false",
                    "gigafiber.subsystems.traffic.enabled" to "false",
                ),
            ),
        )
        val settings = mapOf<String, Any>(
            AvailableSettings.URL to jdbc,
            AvailableSettings.USER to user,
            AvailableSettings.PASS to password,
            AvailableSettings.DIALECT to "org.hibernate.spatial.dialect.mysql.MySQL56InnoDBSpatialDialect",
            AvailableSettings.HBM2DDL_AUTO to "validate",
            AvailableSettings.PHYSICAL_NAMING_STRATEGY to SpringPhysicalNamingStrategy::class.java.name,
            AvailableSettings.IMPLICIT_NAMING_STRATEGY to SpringImplicitNamingStrategy::class.java.name,
            "hibernate.auto_quote_keyword" to "true",
            AvailableSettings.SHOW_SQL to false,
        )
        val registry = StandardServiceRegistryBuilder().applySettings(settings).build()
        try {
            val sources = MetadataSources(registry)
            val scanner = ClassPathScanningCandidateComponentProvider(false)
            scanner.addIncludeFilter(AnnotationTypeFilter(Entity::class.java))
            SubsystemScanFilter.entityPackages(environment).forEach { pkg ->
                scanner.findCandidateComponents(pkg).forEach { def ->
                    sources.addAnnotatedClass(Class.forName(def.beanClassName))
                }
            }
            sources.buildMetadata().buildSessionFactory().close()
        } finally {
            StandardServiceRegistryBuilder.destroy(registry)
        }
    }

    private fun jdbcCatalog(jdbc: String): String {
        val withoutQuery = jdbc.substringBefore('?').trimEnd('/')
        return withoutQuery.substringAfterLast('/')
    }
}
