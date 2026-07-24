package com.dscorp.wispadmin.observability.config

import com.dscorp.wispadmin.observability.tracing.ObsTracer
import net.ttddyy.dsproxy.ExecutionInfo
import net.ttddyy.dsproxy.QueryInfo
import net.ttddyy.dsproxy.listener.QueryExecutionListener
import net.ttddyy.dsproxy.support.ProxyDataSource
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.stereotype.Component
import javax.sql.DataSource

@Component
class DataSourceTracingBeanPostProcessor : BeanPostProcessor, BeanFactoryAware {

    private lateinit var beanFactory: BeanFactory

    override fun setBeanFactory(beanFactory: BeanFactory) {
        this.beanFactory = beanFactory
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
        if (bean is DataSource && bean !is ProxyDataSource) {
            return ProxyDataSourceBuilder.create(bean)
                .name("obs-traced")
                .listener(tracingListener())
                .build()
        }
        return bean
    }

    private fun tracingListener(): QueryExecutionListener =
        object : QueryExecutionListener {
            override fun beforeQuery(execInfo: ExecutionInfo?, queryInfoList: MutableList<QueryInfo>?) {}

            override fun afterQuery(execInfo: ExecutionInfo?, queryInfoList: MutableList<QueryInfo>?) {
                if (execInfo == null) return
                val properties = resolveProperties() ?: return
                if (!properties.enabled || !properties.tracing.enabled) return
                val statement = queryInfoList
                    ?.mapNotNull { it.query?.takeIf { q -> q.isNotBlank() } }
                    ?.joinToString("; ")
                    ?.takeIf { it.isNotBlank() } ?: return
                val obsTracer = resolveTracer() ?: return
                obsTracer.recordDbSpan(statement, execInfo.elapsedTime, execInfo.isSuccess)
            }
        }

    private fun resolveProperties(): ObservabilityProperties? =
        try {
            beanFactory.getBean(ObservabilityProperties::class.java)
        } catch (e: BeansException) {
            null
        }

    private fun resolveTracer(): ObsTracer? =
        try {
            beanFactory.getBean(ObsTracer::class.java)
        } catch (e: BeansException) {
            null
        }
}
