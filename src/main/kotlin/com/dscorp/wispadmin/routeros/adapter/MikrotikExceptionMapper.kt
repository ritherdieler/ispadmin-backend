package com.dscorp.wispadmin.routeros.adapter

import com.dscorp.wispadmin.routeros.port.MikrotikAuthException
import com.dscorp.wispadmin.routeros.port.MikrotikCommandException
import com.dscorp.wispadmin.routeros.port.MikrotikException
import com.dscorp.wispadmin.routeros.port.MikrotikTimeoutException
import com.dscorp.wispadmin.routeros.port.MikrotikUnreachableException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

object MikrotikExceptionMapper {

    fun map(error: Throwable, context: String): MikrotikException {
        if (error is MikrotikException) {
            return error
        }
        val message = "$context: ${error.message ?: error.javaClass.simpleName}"
        if (isTimeout(error)) {
            return MikrotikTimeoutException(message, error)
        }
        if (isAuth(error)) {
            return MikrotikAuthException(message, error)
        }
        if (isUnreachable(error)) {
            return MikrotikUnreachableException(message, error)
        }
        return MikrotikCommandException(message, error)
    }

    fun mapHttp(statusCode: Int, body: String?, context: String): MikrotikException {
        val detail = body?.take(300).orEmpty()
        val message = "$context: HTTP $statusCode $detail".trim()
        return when (statusCode) {
            401, 403 -> MikrotikAuthException(message)
            408, 504 -> MikrotikTimeoutException(message)
            else -> MikrotikCommandException(message)
        }
    }

    private fun isTimeout(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is SocketTimeoutException || current is TimeoutException) {
                return true
            }
            val text = (current.message ?: "").lowercase()
            if (text.contains("timed out") || text.contains("timeout")) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun isAuth(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val text = (current.message ?: "").lowercase()
            if (text.contains("cannot log in") ||
                text.contains("invalid user") ||
                text.contains("login failure") ||
                text.contains("authentication") ||
                text.contains("unauthorized")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun isUnreachable(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is ConnectException ||
                current is UnknownHostException ||
                current is NoRouteToHostException
            ) {
                return true
            }
            if (current is IOException) {
                val text = (current.message ?: "").lowercase()
                if (text.contains("connection refused") ||
                    text.contains("network is unreachable") ||
                    text.contains("failed to connect")
                ) {
                    return true
                }
            }
            current = current.cause
        }
        return false
    }
}
