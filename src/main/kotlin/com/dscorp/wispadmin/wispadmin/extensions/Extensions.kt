package com.dscorp.wispadmin.wispadmin.extensions

import com.dscorp.wispadmin.wispadmin.controller.ModuleException
import com.dscorp.wispadmin.wispadmin.data.model.Modules
import com.dscorp.wispadmin.wispadmin.data.model.ErrorLog
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.dto.NetworkDeviceDto
import com.dscorp.wispadmin.wispadmin.service.EnvironmentService
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import me.legrange.mikrotik.ApiConnection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

interface NetworkDeviceConnection {
    val ipAddress: String?
    val username: String?
    val password: String?
}

@Component
class NetworkDeviceConnectionHelper {
    
    @Autowired
    private lateinit var environmentService: EnvironmentService
    
    @Autowired
    private lateinit var networkDeviceRepository: NetworkDeviceRepository

    @Value("\${mikrotik.connection.mock.enabled:true}")
    private lateinit var mikrotikMockEnabled: String

    @Value("\${mikrotik.connection.override.ip:}")
    private lateinit var mikrotikOverrideIp: String

    @Value("\${mikrotik.connection.override.username:}")
    private lateinit var mikrotikOverrideUsername: String

    @Value("\${mikrotik.connection.override.password:}")
    private lateinit var mikrotikOverridePassword: String
    
    fun getConnectionData(device: NetworkDeviceConnection): NetworkDeviceConnection {
        if (environmentService.isDevelopment()) {
            if (mikrotikOverrideIp.isNotBlank()) {
                return object : NetworkDeviceConnection {
                    override val ipAddress: String? = mikrotikOverrideIp
                    override val username: String? = mikrotikOverrideUsername.ifBlank { device.username }
                    override val password: String? = mikrotikOverridePassword.ifBlank { device.password }
                }
            }

            if (!mikrotikMockEnabled.toBoolean()) {
                return device
            }

            val testDevice = networkDeviceRepository.findByName("mikrotik_test")
                ?: throw RuntimeException("No se encontró el dispositivo de prueba 'mikrotik_test' en ambiente de desarrollo")
            
            return object : NetworkDeviceConnection {
                override val ipAddress: String? = testDevice.ipAddress
                override val username: String? = testDevice.username
                override val password: String? = testDevice.password
            }
        }
        return device
    }

    fun isMikroTikMockModeEnabled(): Boolean {
        return environmentService.isDevelopment() &&
                mikrotikOverrideIp.isBlank() &&
                mikrotikMockEnabled.toBoolean()
    }
}

object NetworkDeviceConnectionManager {
    private var helper: NetworkDeviceConnectionHelper? = null
    
    fun setHelper(helper: NetworkDeviceConnectionHelper) {
        this.helper = helper
    }
    
    fun getConnectionData(device: NetworkDeviceConnection): NetworkDeviceConnection {
        return helper?.getConnectionData(device) ?: device
    }

    fun isMikroTikMockModeEnabled(): Boolean {
        return helper?.isMikroTikMockModeEnabled() ?: false
    }
}


// remove /24 from ip range
fun String.getBaseIpFromRange(): String {
    val segment = this.split("/")
    val a = segment[0].split(".")
    val b = a.subList(0, a.size - 1)
    val c =b.reduce { acc, s -> "$acc.$s" }+"."
    return c
}

fun NetworkDeviceConnection.executeCommand(block: (connection: ApiConnection) -> Unit) {
    if (NetworkDeviceConnectionManager.isMikroTikMockModeEnabled()) {
        return
    }

    val connectionData = NetworkDeviceConnectionManager.getConnectionData(this)
    ApiConnection.connect(connectionData.ipAddress).also {
        it.login(connectionData.username, connectionData.password)
        block.invoke(it)
        it.close()
    }
}


fun Long.toFormattedDate(): String {
    val date = Date(this)
    val format = SimpleDateFormat("dd/MM/yyyy")
    return format.format(date)
}

fun Calendar.getFirstDayOfMonthInMillis(): Long {
    this.set(Calendar.DAY_OF_MONTH, 1)
    this.set(Calendar.HOUR_OF_DAY, 0)
    this.set(Calendar.MINUTE, 0)
    this.set(Calendar.SECOND, 0)
    this.set(Calendar.MILLISECOND, 0)
    return this.timeInMillis
}

fun Calendar.getLastDayOfMonthInMillis(): Long {
    this.set(Calendar.DAY_OF_MONTH, this.getActualMaximum(Calendar.DAY_OF_MONTH))
    this.set(Calendar.HOUR_OF_DAY, 23)
    this.set(Calendar.MINUTE, 59)
    this.set(Calendar.SECOND, 59)
    this.set(Calendar.MILLISECOND, 999)
    return this.timeInMillis
}


fun Calendar.getDateWithoutTime(): Long {
    this.set(Calendar.HOUR_OF_DAY, 0)
    this.set(Calendar.MINUTE, 0)
    this.set(Calendar.SECOND, 0)
    this.set(Calendar.MILLISECOND, 0)
    return this.timeInMillis
}

fun String.removeSpecialCharacters(): String {
    return this.replace("[^a-zA-Z ]".toRegex(), "").replace("[0-9]".toRegex(), "").replace("\n", "")
}

fun Exception.toErrorLog(module: Modules): ErrorLog {
    val error = ErrorLog(
        module = module.name,
        error = this.message,

    )

    if (this is ModuleException)
        error.module = this.moduleName

    return error
}
