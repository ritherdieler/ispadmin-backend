package com.dscorp.wispadmin.traffic.config

import com.dscorp.wispadmin.shared.persistence.SatelliteDataSourceSettings
import com.dscorp.wispadmin.shared.persistence.SatelliteJpa
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

@ConfigurationProperties(prefix = "traffic.datasource")
class TrafficDataSourceSettings : SatelliteDataSourceSettings()

@Configuration
@EnableConfigurationProperties(TrafficDataSourceSettings::class)
@ConditionalOnProperty(prefix = "gigafiber.subsystems.traffic", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@ConditionalOnExpression("'\${traffic.datasource.url:}'.trim().length() > 0")
@EnableJpaRepositories(
    basePackages = ["com.dscorp.wispadmin.traffic"],
    entityManagerFactoryRef = "trafficEntityManagerFactory",
    transactionManagerRef = "trafficTransactionManager",
)
class TrafficPersistenceConfig {

    @Bean(name = ["trafficDataSource"])
    fun trafficDataSource(settings: TrafficDataSourceSettings): DataSource = SatelliteJpa.dataSource(settings)

    @Bean(name = ["trafficEntityManagerFactory"])
    fun trafficEntityManagerFactory(
        builder: EntityManagerFactoryBuilder,
        @Qualifier("trafficDataSource") dataSource: DataSource,
        jpaProperties: JpaProperties,
        environment: Environment,
    ): LocalContainerEntityManagerFactoryBean =
        SatelliteJpa.entityManagerFactory(
            builder = builder,
            dataSource = dataSource,
            packages = "com.dscorp.wispadmin.traffic",
            persistenceUnit = "traffic",
            ddlAuto = environment.getProperty("traffic.jpa.hibernate.ddl-auto", "update"),
            jpaProperties = jpaProperties,
        )

    @Bean(name = ["trafficTransactionManager"])
    fun trafficTransactionManager(
        @Qualifier("trafficEntityManagerFactory") factory: EntityManagerFactory,
    ): PlatformTransactionManager = SatelliteJpa.transactionManager(factory)
}
