package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsAlertRule
import com.dscorp.wispadmin.observability.entity.ObsAlertType
import com.dscorp.wispadmin.observability.entity.ObsIssue
import com.dscorp.wispadmin.observability.repository.ObsAlertRuleRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class ObsIssueAlertHandler(
    private val ruleRepository: ObsAlertRuleRepository,
    private val dispatcher: ObsAlertDispatcher,
    private val properties: ObservabilityProperties
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    @Async("obsTaskExecutor")
    fun onIssuePersisted(issue: ObsIssue, isNew: Boolean, wasReopened: Boolean) {
        if (!properties.alerts.enabled) return
        try {
            if (isNew) {
                evaluateRules(ObsAlertType.NEW_ISSUE, issue, "new-issue:${issue.id}")
            }
            if (wasReopened) {
                evaluateRules(ObsAlertType.ISSUE_REGRESSION, issue, "regression:${issue.id}")
            }
        } catch (e: Exception) {
            log.warn("Error evaluando alertas de issue {}: {}", issue.id, e.message)
        }
    }

    private fun evaluateRules(type: ObsAlertType, issue: ObsIssue, dedupKey: String) {
        val rules = ruleRepository.findByEnabledTrueAndType(type)
        rules.filter { matches(it, issue) }.forEach { rule ->
            val prefix = if (type == ObsAlertType.NEW_ISSUE) "Nuevo issue" else "Regresión de issue"
            val title = "$prefix: ${issue.title ?: issue.errorType ?: "sin título"}"
            val message = buildString {
                append("Plataforma: ${issue.platform ?: "-"}\n")
                append("Severidad: ${issue.severity ?: "-"}\n")
                append("Ocurrencias: ${issue.eventCount}")
                issue.lastMessage?.let { append("\n${it.take(300)}") }
            }
            dispatcher.dispatch(
                rule = rule,
                title = title,
                message = message,
                dedupKey = dedupKey,
                issueId = issue.id
            )
        }
    }

    private fun matches(rule: ObsAlertRule, issue: ObsIssue): Boolean {
        if (!rule.platform.isNullOrBlank() && rule.platform != issue.platform) return false
        if (!rule.severity.isNullOrBlank() && rule.severity != issue.severity) return false
        if (!rule.environment.isNullOrBlank() && rule.environment != issue.lastEnvironment) return false
        return true
    }
}
