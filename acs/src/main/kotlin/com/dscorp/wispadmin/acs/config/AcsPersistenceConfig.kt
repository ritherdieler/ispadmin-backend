package com.dscorp.wispadmin.acs.config

import com.dscorp.wispadmin.shared.persistence.SatelliteDataSourceSettings
import com.dscorp.wispadmin.shared.persistence.SatelliteJpa
import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.transaction.PlatformTransactionManager
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

@ConfigurationProperties(prefix = "acs.datasource")
class AcsDataSourceSettings : SatelliteDataSourceSettings()

@Configuration
@EnableConfigurationProperties(AcsDataSourceSettings::class)
@ConditionalOnProperty(prefix = "gigafiber.subsystems.acs", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("'\${acs.datasource.url:}'.trim().length() > 0")
@EnableJpaRepositories(
    basePackages = ["com.dscorp.wispadmin.acs"],
    entityManagerFactoryRef = "acsEntityManagerFactory",
    transactionManagerRef = "acsTransactionManager",
)
class AcsPersistenceConfig {

    @Bean(name = ["acsDataSource"])
    fun acsDataSource(settings: AcsDataSourceSettings): DataSource = SatelliteJpa.dataSource(settings)

    @Bean(name = ["acsEntityManagerFactory"])
    fun acsEntityManagerFactory(
        builder: EntityManagerFactoryBuilder,
        @Qualifier("acsDataSource") dataSource: DataSource,
        jpaProperties: JpaProperties,
        environment: Environment,
    ): LocalContainerEntityManagerFactoryBean =
        SatelliteJpa.entityManagerFactory(
            builder = builder,
            dataSource = dataSource,
            packages = "com.dscorp.wispadmin.acs",
            persistenceUnit = "acs",
            ddlAuto = environment.getProperty("acs.jpa.hibernate.ddl-auto", "update"),
            jpaProperties = jpaProperties,
        )

    @Bean(name = ["acsTransactionManager"])
    fun acsTransactionManager(
        @Qualifier("acsEntityManagerFactory") factory: EntityManagerFactory,
    ): PlatformTransactionManager = SatelliteJpa.transactionManager(factory)

    @Bean(name = ["acsFlyway"])
    fun acsFlyway(
        @Qualifier("acsDataSource") dataSource: DataSource,
        environment: Environment,
    ): Flyway? =
        SatelliteJpa.migrateIfEnabled(
            dataSource = dataSource,
            locations = "classpath:db/acs",
            enabled = environment.getProperty("acs.flyway.enabled", Boolean::class.java, true),
            baselineVersion = environment.getProperty("acs.flyway.baseline-version", "0"),
        )
}
