package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.shared.persistence.SatelliteJpa
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import javax.sql.DataSource

@Configuration
class CoreJpaConfig {

    @Bean(name = ["entityManagerFactory"])
    @Primary
    fun entityManagerFactory(
        builder: EntityManagerFactoryBuilder,
        @Qualifier("dataSource") dataSource: DataSource,
        jpaProperties: JpaProperties,
        environment: Environment,
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
