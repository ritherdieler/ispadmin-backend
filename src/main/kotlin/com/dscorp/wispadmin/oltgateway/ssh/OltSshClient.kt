package com.dscorp.wispadmin.oltgateway.ssh

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.exception.OltUnreachableException
import org.apache.sshd.client.ClientBuilder
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.NamedFactory
import org.apache.sshd.common.cipher.BuiltinCiphers
import org.apache.sshd.common.cipher.Cipher
import org.apache.sshd.common.kex.BuiltinDHFactories
import org.apache.sshd.common.kex.KeyExchangeFactory
import org.apache.sshd.common.signature.BuiltinSignatures
import org.apache.sshd.common.signature.Signature
import org.apache.sshd.core.CoreModuleProperties
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit

class OltSshClient(
    private val properties: OltGatewayProperties,
    private val clientFactory: () -> SshClient = { SshClient.setUpDefaultClient() }
) : AutoCloseable {

    internal companion object {
        private val logger = LoggerFactory.getLogger(OltSshClient::class.java)

        fun applyTransportTimeouts(client: SshClient, properties: OltGatewayProperties) {
            val idleMinutes = properties.session.sshIdleTimeoutMinutes
            if (idleMinutes <= 0) {
                CoreModuleProperties.IDLE_TIMEOUT.set(client, Duration.ZERO)
            } else {
                CoreModuleProperties.IDLE_TIMEOUT.set(client, Duration.ofMinutes(idleMinutes))
            }
            CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, Duration.ZERO)
            CoreModuleProperties.HEARTBEAT_REPLY_WAIT.set(client, Duration.ZERO)
        }
    }

    data class ConnectedSession(
        val client: SshClient,
        val session: ClientSession
    )

    @Volatile
    private var sharedClient: SshClient? = null
    private val clientLock = Any()

    fun openSession(): ConnectedSession {
        val client = ensureClient()
        try {
            val connectFuture = client.connect(
                properties.username,
                properties.host,
                properties.port
            )
            connectFuture.verify(properties.commandTimeoutMs, TimeUnit.MILLISECONDS)
            val session = connectFuture.session
            session.addPasswordIdentity(properties.password)
            session.auth().verify(properties.commandTimeoutMs, TimeUnit.MILLISECONDS)
            logger.info("SSH session established to {}:{}", properties.host, properties.port)
            return ConnectedSession(client, session)
        } catch (ex: Exception) {
            throw OltUnreachableException(
                "Unable to reach OLT at ${properties.host}:${properties.port}",
                ex
            )
        }
    }

    fun close(connected: ConnectedSession?) {
        if (connected == null) return
        try {
            connected.session.close()
        } catch (ex: Exception) {
            logger.warn("Error closing SSH session: {}", ex.message)
        }
    }

    override fun close() {
        synchronized(clientLock) {
            val client = sharedClient
            sharedClient = null
            if (client != null) {
                try {
                    client.stop()
                } catch (ex: Exception) {
                    logger.warn("Error stopping SSH client: {}", ex.message)
                }
            }
        }
    }

    private fun ensureClient(): SshClient {
        synchronized(clientLock) {
            val existing = sharedClient
            if (existing != null && existing.isStarted) {
                return existing
            }
            val client = clientFactory()
            client.serverKeyVerifier = AcceptAllServerKeyVerifier.INSTANCE
            if (properties.ssh.legacyAlgorithms) {
                configureLegacyAlgorithms(client)
            }
            applyTimeouts(client)
            client.start()
            sharedClient = client
            return client
        }
    }

    private fun applyTimeouts(client: SshClient) {
        applyTransportTimeouts(client, properties)
    }

    private fun configureLegacyAlgorithms(client: SshClient) {
        val dh = listOf(
            BuiltinDHFactories.dhgex,
            BuiltinDHFactories.dhg14,
            BuiltinDHFactories.dhg1,
            BuiltinDHFactories.dhgex256,
            BuiltinDHFactories.ecdhp256,
            BuiltinDHFactories.curve25519
        )
        @Suppress("UNCHECKED_CAST")
        client.keyExchangeFactories = NamedFactory.setUpTransformedFactories(
            true,
            dh,
            ClientBuilder.DH2KEX
        ) as List<KeyExchangeFactory>

        val signatures = listOf(
            BuiltinSignatures.rsa,
            BuiltinSignatures.rsaSHA256,
            BuiltinSignatures.rsaSHA512,
            BuiltinSignatures.nistp256,
            BuiltinSignatures.ed25519
        )
        @Suppress("UNCHECKED_CAST")
        client.signatureFactories = NamedFactory.setUpBuiltinFactories(true, signatures)
            as List<NamedFactory<Signature>>

        val ciphers = listOf(
            BuiltinCiphers.aes128cbc,
            BuiltinCiphers.aes128ctr,
            BuiltinCiphers.aes256ctr,
            BuiltinCiphers.aes256cbc
        )
        @Suppress("UNCHECKED_CAST")
        client.cipherFactories = NamedFactory.setUpBuiltinFactories(true, ciphers)
            as List<NamedFactory<Cipher>>
    }
}
