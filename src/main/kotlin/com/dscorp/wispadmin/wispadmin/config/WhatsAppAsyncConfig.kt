package com.dscorp.wispadmin.wispadmin.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
class WhatsAppAsyncConfig {

    @Bean(name = ["whatsAppBatchTaskExecutor"])
    fun whatsAppBatchTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 4
        executor.queueCapacity = 32
        executor.setThreadNamePrefix("wa-batch-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }

    @Bean(name = ["whatsAppInboundPipelineExecutor"])
    fun whatsAppInboundPipelineExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 4
        executor.maxPoolSize = 8
        executor.queueCapacity = 256
        executor.setThreadNamePrefix("wa-inbound-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }
}
