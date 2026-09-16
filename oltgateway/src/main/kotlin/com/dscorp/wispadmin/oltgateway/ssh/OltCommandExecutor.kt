package com.dscorp.wispadmin.oltgateway.ssh

import com.dscorp.wispadmin.oltgateway.exception.CliBusBusyException
import com.dscorp.wispadmin.oltgateway.exception.OltUnreachableException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException

class OltCommandExecutor(
    private val cliBus: OltCliBus,
    private val maxRetryAttempts: Int = 3,
    private val retryDelayMs: Long = 2000
) {

    private val logger = LoggerFactory.getLogger(OltCommandExecutor::class.java)

    fun run(command: String): String = adhoc { it.execute(command) }

    fun <T> adhoc(block: (HuaweiCliSession) -> T): T = executeWithRetry(CliJobType.ADHOC, block)

    fun <T> job(type: CliJobType, block: (HuaweiCliSession) -> T): T = executeWithRetry(type, block)

    fun <T> write(block: (HuaweiCliSession) -> T): T = executeWithRetry(CliJobType.WRITE, block)

    fun <T> authorize(block: (HuaweiCliSession) -> T): T = executeWithRetry(CliJobType.AUTHORIZE, block)

    fun <T> unconfigured(block: (HuaweiCliSession) -> T): T = executeWithRetry(CliJobType.UNCONFIGURED, block)

    fun ping(): Long = cliBus.ping()

    private fun <T> execute(type: CliJobType, block: (HuaweiCliSession) -> T): T {
        return when (val result = cliBus.execute(type, block)) {
            is CliBusResult.Ok -> result.value
            is CliBusResult.Skipped -> throw CliBusBusyException(result.reason)
        }
    }

    private fun <T> executeWithRetry(type: CliJobType, block: (HuaweiCliSession) -> T): T {
        var attempt = 0
        var lastException: Exception? = null

        while (attempt <= maxRetryAttempts) {
            try {
                return execute(type, block)
            } catch (ex: Exception) {
                lastException = ex

                if (!isConnectionFailure(ex) || attempt >= maxRetryAttempts) {
                    throw ex
                }

                attempt++
                logger.warn("OLT SSH connection failed (attempt $attempt/${maxRetryAttempts + 1}): ${ex.message}")

                if (attempt <= maxRetryAttempts) {
                    try {
                        Thread.sleep(retryDelayMs)
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw interrupted
                    }
                }
            }
        }

        throw lastException ?: IllegalStateException("Unexpected retry loop exit")
    }

    private fun isConnectionFailure(ex: Exception): Boolean {
        var current: Throwable? = ex
        while (current != null) {
            if (current is OltUnreachableException) {
                return true
            }

            if (current is IOException || current is SocketException ||
                current is SocketTimeoutException || current is ConnectException
            ) {
                return true
            }

            val message = current.message.orEmpty().lowercase()
            if (message.contains("connection refused") ||
                message.contains("connection reset") ||
                message.contains("read timeout") ||
                message.contains("unable to reach olt") ||
                message.contains("ssh session down")
            ) {
                return true
            }

            current = current.cause
        }
        return false
    }
}
