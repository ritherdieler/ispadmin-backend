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
    private val maxRetryAttempts: Int = 0,
    private val retryDelayMs: Long = 2000
) {

    private val logger = LoggerFactory.getLogger(OltCommandExecutor::class.java)

    fun run(command: String): String = adhoc { it.execute(command) }

    fun <T> adhoc(block: (HuaweiCliSession) -> T): T = executeWithRetry(CliJobType.ADHOC, block)

    fun <T> job(type: CliJobType, block: (HuaweiCliSession) -> T): T = executeWithRetry(type, block)

    fun <T> write(block: (HuaweiCliSession) -> T): T = executeWithRetry(CliJobType.WRITE, block)

    /** v2 remote effects must surface a retriable failure instead of monopolizing the SSH lane. */
    fun <T> writeBounded(maxRetryAttempts: Int, block: (HuaweiCliSession) -> T): T {
        require(maxRetryAttempts >= 0)
        return executeWithRetry(CliJobType.WRITE, block, maxRetryAttempts)
    }

    fun <T> authorize(block: (HuaweiCliSession) -> T): T = executeWithRetry(CliJobType.AUTHORIZE, block)

    fun <T> unconfigured(block: (HuaweiCliSession) -> T): T = executeWithRetry(CliJobType.UNCONFIGURED, block)

    fun ping(): Long = cliBus.ping()

    private fun <T> execute(type: CliJobType, block: (HuaweiCliSession) -> T): T {
        return when (val result = cliBus.execute(type, block)) {
            is CliBusResult.Ok -> result.value
            is CliBusResult.Skipped -> throw CliBusBusyException(result.reason)
        }
    }

    private fun <T> executeWithRetry(
        type: CliJobType,
        block: (HuaweiCliSession) -> T,
        retryAttempts: Int = maxRetryAttempts,
    ): T {
        var attempt = 0
        val isUnlimited = retryAttempts == 0

        while (true) {
            try {
                return execute(type, block)
            } catch (ex: Exception) {
                if (!isConnectionFailure(ex)) {
                    throw ex
                }

                if (!isUnlimited && attempt >= retryAttempts) {
                    throw ex
                }

                attempt++
                val attemptDisplay = if (isUnlimited) "$attempt" else "$attempt/${retryAttempts + 1}"
                logger.warn("OLT SSH connection failed (attempt $attemptDisplay): ${ex.message}")

                try {
                    Thread.sleep(retryDelayMs)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw interrupted
                }
            }
        }
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
