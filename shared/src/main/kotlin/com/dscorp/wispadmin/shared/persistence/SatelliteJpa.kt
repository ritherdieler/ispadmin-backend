package com.dscorp.wispadmin.shared.persistence

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

open class SatelliteDataSourceSettings {
    var url: String = ""
    var username: String = ""
    var password: String = ""
}

object SatelliteJpa {
    fun configured(settings: SatelliteDataSourceSettings): Boolean = settings.url.trim().isNotEmpty()

    fun dataSource(settings: SatelliteDataSourceSettings): DataSource =
        DataSourceBuilder.create()
            .type(HikariDataSource::class.java)
            .url(settings.url)
            .username(settings.username)
            .password(settings.password)
            .build()

    const val UNICODE_CI_DIALECT = "com.dscorp.wispadmin.shared.persistence.UnicodeCiMySQLDialect"

    fun entityManagerFactory(
        builder: EntityManagerFactoryBuilder,
        dataSource: DataSource,
        packages: String,
        persistenceUnit: String,
        ddlAuto: String,
        jpaProperties: JpaProperties,
        dialect: String? = null,
    ): LocalContainerEntityManagerFactoryBean =
        entityManagerFactory(builder, dataSource, arrayOf(packages), persistenceUnit, ddlAuto, jpaProperties, dialect)

    fun entityManagerFactory(
        builder: EntityManagerFactoryBuilder,
        dataSource: DataSource,
        packages: Array<String>,
        persistenceUnit: String,
        ddlAuto: String,
        jpaProperties: JpaProperties,
        dialect: String? = null,
    ): LocalContainerEntityManagerFactoryBean {
        val properties = jpaProperties.properties.toMutableMap()
        properties["hibernate.hbm2ddl.auto"] = ddlAuto
        if (!dialect.isNullOrBlank()) {
            properties["hibernate.dialect"] = dialect
        }
        properties.putIfAbsent(
            "hibernate.physical_naming_strategy",
            "org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy",
        )
        properties.putIfAbsent(
            "hibernate.implicit_naming_strategy",
            "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy",
        )
        return builder
            .dataSource(dataSource)
            .packages(*packages)
            .persistenceUnit(persistenceUnit)
            .properties(properties)
            .build()
    }

    fun transactionManager(factory: EntityManagerFactory): JpaTransactionManager =
        JpaTransactionManager(factory)

    fun migrateIfEnabled(
        dataSource: DataSource,
        locations: String,
        enabled: Boolean,
        baselineVersion: String,
    ): Flyway? {
        if (!enabled) {
            return null
        }
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(locations)
            .baselineOnMigrate(true)
            .baselineVersion(baselineVersion)
            .load()
        flyway.repair()
        flyway.migrate()
        return flyway
    }
}
