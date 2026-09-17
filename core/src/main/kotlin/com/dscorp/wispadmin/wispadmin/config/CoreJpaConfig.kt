package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.shared.persistence.SatelliteJpa
import com.dscorp.wispadmin.wispadmin.schema.ApplyCoreFlywayUseCase
import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import javax.sql.DataSource

@Configuration
class CoreJpaConfig {

    @Bean(name = ["coreFlyway"])
    fun coreFlyway(
        @Qualifier("dataSource") dataSource: DataSource,
        environment: Environment,
    ): Flyway {
        val enabled = environment.getProperty("spring.flyway.enabled", Boolean::class.java, false)
        val locations = environment.getProperty(
            "spring.flyway.locations",
            ApplyCoreFlywayUseCase.DEFAULT_LOCATIONS,
        )
        val baseline = environment.getProperty(
            "spring.flyway.baseline-version",
            ApplyCoreFlywayUseCase.DEFAULT_BASELINE,
        )
        if (!enabled) {
            return Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .load()
        }
        return ApplyCoreFlywayUseCase(SatelliteJpa::migrateIfEnabled)
            .invoke(
                dataSource = dataSource,
                locations = locations,
                enabled = true,
                baselineVersion = baseline,
            )
            .getOrThrow()
            ?: error("coreFlyway migrate returned null")
    }

    @Bean(name = ["entityManagerFactory"])
    @Primary
    @DependsOn("coreFlyway")
    @Suppress("UNUSED_PARAMETER")
    fun entityManagerFactory(
        builder: EntityManagerFactoryBuilder,
        @Qualifier("dataSource") dataSource: DataSource,
        jpaProperties: JpaProperties,
        environment: Environment,
        @Qualifier("coreFlyway") coreFlyway: Flyway,
    ): LocalContainerEntityManagerFactoryBean =
        SatelliteJpa.entityManagerFactory(
            builder = builder,
            dataSource = dataSource,
            packages = SubsystemScanFilter.entityPackages(environment),
            persistenceUnit = "default",
            ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto", "update"),
            jpaProperties = jpaProperties,
        )
}
