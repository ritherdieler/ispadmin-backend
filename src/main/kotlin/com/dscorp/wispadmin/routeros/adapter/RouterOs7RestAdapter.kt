package com.dscorp.wispadmin.routeros.adapter

import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import org.springframework.core.io.DefaultResourceLoader
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class RouterOs7RestAdapter(
    private val properties: RouterOsClientProperties,
    private val objectMapper: ObjectMapper = ObjectMapper(),
    httpClient: OkHttpClient? = null
) : MikrotikClient, AutoCloseable {

    private val ownedClient: OkHttpClient = httpClient ?: buildClient(properties)

    override fun <T> withSession(device: MikrotikDeviceRef, block: (MikrotikSession) -> T): T {
        val effective = device.copy(
            port = if (device.port > 0) device.port else properties.rest.port
        )
        return try {
            block(
                RouterOs7RestSession(
                    device = effective,
                    scheme = properties.rest.scheme,
                    httpClient = ownedClient,
                    objectMapper = objectMapper
                )
            )
        } catch (error: Exception) {
            throw MikrotikExceptionMapper.map(error, "rest ${effective.host}:${effective.port}")
        }
    }

    override fun close() {
    }

    companion object {
        fun buildClient(properties: RouterOsClientProperties): OkHttpClient {
            val timeoutMs = properties.rest.timeoutMs.coerceAtLeast(1000)
            val builder = OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs + 1000, TimeUnit.MILLISECONDS)

            if (!properties.rest.verifySsl) {
                val trustAll = trustAllManager()
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustAll)
                builder.hostnameVerifier { _, _ -> true }
                return builder.build()
            }

            val trustStoreLocation = properties.rest.trustStore
            if (trustStoreLocation.isNotBlank()) {
                val trustManager = trustManagerFromStore(
                    trustStoreLocation,
                    properties.rest.trustStorePassword
                )
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            }
            return builder.build()
        }

        private fun trustAllManager(): X509TrustManager {
            return object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        }

        private fun trustManagerFromStore(location: String, password: String): X509TrustManager {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            openResource(location).use { input ->
                keyStore.load(input, password.toCharArray())
            }
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(keyStore)
            return factory.trustManagers
                .filterIsInstance<X509TrustManager>()
                .firstOrNull()
                ?: throw IllegalStateException("No X509TrustManager in truststore $location")
        }

        private fun openResource(location: String): InputStream {
            val loader = DefaultResourceLoader()
            val resource = loader.getResource(location)
            if (!resource.exists()) {
                throw IllegalStateException("Truststore not found: $location")
            }
            return resource.inputStream
        }
    }
}
