package com.dscorp.wispadmin.oltgateway.config

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

@ConfigurationProperties(prefix = "oltgateway.datasource")
class OltGatewayDataSourceSettings : SatelliteDataSourceSettings()

@Configuration
@EnableConfigurationProperties(OltGatewayDataSourceSettings::class)
@ConditionalOnProperty(prefix = "gigafiber.subsystems.oltgateway", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("'\${oltgateway.datasource.url:}'.trim().length() > 0")
@EnableJpaRepositories(
    basePackages = ["com.dscorp.wispadmin.oltgateway"],
    entityManagerFactoryRef = "oltGatewayEntityManagerFactory",
    transactionManagerRef = "oltGatewayTransactionManager",
)
class OltGatewayPersistenceConfig {

    @Bean(name = ["oltGatewayDataSource"])
    fun oltGatewayDataSource(settings: OltGatewayDataSourceSettings): DataSource = SatelliteJpa.dataSource(settings)

    @Bean(name = ["oltGatewayEntityManagerFactory"])
    fun oltGatewayEntityManagerFactory(
        builder: EntityManagerFactoryBuilder,
        @Qualifier("oltGatewayDataSource") dataSource: DataSource,
        jpaProperties: JpaProperties,
        environment: Environment,
    ): LocalContainerEntityManagerFactoryBean =
        SatelliteJpa.entityManagerFactory(
            builder = builder,
            dataSource = dataSource,
            packages = "com.dscorp.wispadmin.oltgateway",
            persistenceUnit = "oltgateway",
            ddlAuto = environment.getProperty("oltgateway.jpa.hibernate.ddl-auto", "update"),
            jpaProperties = jpaProperties,
        )

    @Bean(name = ["oltGatewayTransactionManager"])
    fun oltGatewayTransactionManager(
        @Qualifier("oltGatewayEntityManagerFactory") factory: EntityManagerFactory,
    ): PlatformTransactionManager = SatelliteJpa.transactionManager(factory)

    @Bean(name = ["oltGatewayFlyway"])
    fun oltGatewayFlyway(
        @Qualifier("oltGatewayDataSource") dataSource: DataSource,
        environment: Environment,
    ): Flyway? =
        SatelliteJpa.migrateIfEnabled(
            dataSource = dataSource,
            locations = "classpath:db/oltgateway",
            enabled = environment.getProperty("oltgateway.flyway.enabled", Boolean::class.java, true),
            baselineVersion = environment.getProperty("oltgateway.flyway.baseline-version", "0"),
        )
}
