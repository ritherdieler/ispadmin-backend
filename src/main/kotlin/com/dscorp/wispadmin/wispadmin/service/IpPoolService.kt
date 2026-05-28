package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.IpPool
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.IpPoolRepository
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.requestbody.IpPoolRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.DefaultTransactionDefinition

@Service
class IpPoolService(
    private val repository: IpPoolRepository,
    private val networkDeviceRepository: NetworkDeviceRepository,

    ) {

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    /**
     * Método privado para eliminar una dirección IP del dispositivo de red por ID
     * @param hostDevice El dispositivo de red donde se eliminará la dirección
     * @param ipSegment El segmento IP a eliminar
     */
    private fun removeIpAddressById(hostDevice: com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice, ipSegment: String) {
        hostDevice.executeCommand {
            // Primero obtener todas las direcciones IP
            val addressesQuery = "/ip/address/print"
            val addresses = it.execute(addressesQuery)
            
            // Buscar la dirección que coincida con el segmento IP
            val targetAddress = addresses.find { address ->
                address["address"] == ipSegment
            }
            
            // Si se encuentra la dirección, eliminar por ID
            targetAddress?.let { address ->
                val addressId = address[".id"]
                if (addressId != null) {
                    val removeQuery = "/ip/address/remove .id=$addressId"
                    it.execute(removeQuery)
                }
            }
        }
    }

    @Transactional(rollbackFor = [Exception::class])  // Rollback explícito para cualquier excepción
    fun registerIpPool(newIpPool: IpPoolRequest): IpPool? {
        val hostDevice = networkDeviceRepository.findById(newIpPool.hostDeviceId).get()
        val IpPool = IpPool(id = -1, ipSegment = newIpPool.ipSegment, hostDevice = hostDevice)

        try {
            // Guardar en base de datos (dentro de la transacción)
            val savedIpPool = repository.save(IpPool)

            // Registrar en MikroTik (si falla, se hace rollback automático de la BD)
            hostDevice.executeCommand {
                val query = "/ip/address/add address=${newIpPool.ipSegment} interface=LAN comment='NO BORRAR - GENERADO POR ISP ADMIN'"
                it.execute(query)
            }

            return savedIpPool
        } catch (e: Exception) {
            // Si ocurre una excepción al ejecutar el comando, Spring hará rollback de la transacción
            println("Error al registrar IP en MikroTik: ${e.message}")
            throw e  // Rethrow para asegurarse de que Spring haga rollback
        }
    }

    fun updateIpPool(id: Int, updateIpPool: IpPoolRequest): IpPool {
        val transactionDefinition = DefaultTransactionDefinition()
        val transactionStatus = transactionManager.getTransaction(transactionDefinition)
        try {
            val existingIpPool = repository.findById(id).orElseThrow {
                RuntimeException("IP Pool con ID $id no encontrado")
            }

            // Validar si el segmento IP ha cambiado
            if (existingIpPool.ipSegment != updateIpPool.ipSegment) {
                // Verificar si hay suscripciones asociadas
                if (existingIpPool.ips.isNotEmpty()) {
                    throw RuntimeException("No se puede modificar el segmento IP del pool '${existingIpPool.ipSegment}' porque tiene ${existingIpPool.ips.size} suscripción(es) asociada(s). Solo se puede modificar el dispositivo de red.")
                }
            }

            val hostDevice = networkDeviceRepository.findById(updateIpPool.hostDeviceId).get()

            // Actualizar los campos del IP Pool
            existingIpPool.ipSegment = updateIpPool.ipSegment

            val updatedIpPool = repository.save(existingIpPool)

            // Actualizar en el dispositivo de red si es necesario
//            hostDevice.executeCommand {
//                val query = "/ip/address/set address=${updateIpPool.ipSegment} interface=LAN comment='NO BORRAR - GENERADO POR ISP ADMIN'"
//                it.execute(query)
//            }

            transactionManager.commit(transactionStatus)
            return updatedIpPool
        } catch (e: Exception) {
            transactionManager.rollback(transactionStatus)
            e.printStackTrace()
            throw e
        }
    }

    @Transactional
    fun deleteIpPool(id: Int) {
        try {
            val ipPool = repository.findById(id).orElseThrow {
                RuntimeException("IP Pool con ID $id no encontrado")
            }

            // Verificar si hay suscripciones asociadas
            if (ipPool.ips.isNotEmpty()) {
                throw RuntimeException("No se puede eliminar el IP Pool '${ipPool.ipSegment}' porque tiene ${ipPool.ips.size} suscripción(es) asociada(s).")
            }
            
            // Usar el método reutilizable para eliminar la dirección IP
            ipPool.hostDevice?.let { hostDevice ->
                removeIpAddressById(hostDevice, ipPool.ipSegment)
            }
            
            // Hard delete - eliminar completamente de la base de datos
            repository.deleteById(id)

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    fun activateIpPool(id: Int): IpPool {
        val transactionDefinition = DefaultTransactionDefinition()
        val transactionStatus = transactionManager.getTransaction(transactionDefinition)
        try {
            val ipPool = repository.findById(id).orElseThrow {
                RuntimeException("IP Pool con ID $id no encontrado")
            }

            // Activar el IP Pool
            ipPool.isEligible = true
            val activatedIpPool = repository.save(ipPool)

//            // Agregar de vuelta al dispositivo de red
//            ipPool.hostDevice?.executeCommand {
//                val query = "/ip/address/add address=${ipPool.ipSegment} interface=LAN comment='NO BORRAR - GENERADO POR ISP ADMIN'"
//                it.execute(query)
//            }

            transactionManager.commit(transactionStatus)
            return activatedIpPool
        } catch (e: Exception) {
            transactionManager.rollback(transactionStatus)
            e.printStackTrace()
            throw e
        }
    }

    fun deactivateIpPool(id: Int): IpPool {
        val transactionDefinition = DefaultTransactionDefinition()
        val transactionStatus = transactionManager.getTransaction(transactionDefinition)
        try {
            val ipPool = repository.findById(id).orElseThrow {
                RuntimeException("IP Pool con ID $id no encontrado")
            }

            // Desactivar el IP Pool
            ipPool.isEligible = false
            val deactivatedIpPool = repository.save(ipPool)

//            // Remover del dispositivo de red
//            ipPool.hostDevice?.executeCommand {
//                val query = "/ip/address/remove [find address~\"${ipPool.ipSegment}\"]"
//                it.execute(query)
//            }

            transactionManager.commit(transactionStatus)
            return deactivatedIpPool
        } catch (e: Exception) {
            transactionManager.rollback(transactionStatus)
            e.printStackTrace()
            throw e
        }
    }

}