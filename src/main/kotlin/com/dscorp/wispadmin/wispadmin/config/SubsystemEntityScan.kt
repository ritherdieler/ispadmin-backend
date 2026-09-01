package com.dscorp.wispadmin.wispadmin.config

import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.boot.autoconfigure.domain.EntityScanPackages
import org.springframework.context.EnvironmentAware
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar
import org.springframework.core.env.Environment
import org.springframework.core.type.AnnotationMetadata

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(SubsystemEntityScanRegistrar::class)
annotation class SubsystemEntityScan

class SubsystemEntityScanRegistrar : ImportBeanDefinitionRegistrar, EnvironmentAware {

    private lateinit var environment: Environment

    override fun setEnvironment(environment: Environment) {
        this.environment = environment
    }

    override fun registerBeanDefinitions(importingClassMetadata: AnnotationMetadata, registry: BeanDefinitionRegistry) {
        EntityScanPackages.register(registry, *SubsystemScanFilter.entityPackages(environment))
    }
}
