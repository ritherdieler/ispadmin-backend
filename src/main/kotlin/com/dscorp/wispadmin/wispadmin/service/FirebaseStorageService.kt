package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.firebase.FirebaseProperties
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.io.InputStream

@Service
class FirebaseStorageService @Autowired constructor(
    private val firebaseProperties: FirebaseProperties
) {

    @Value("\${gcp.firebase.bucket-name:ispadmin-687ca.appspot.com}")
    private lateinit var bucketName: String

    private val imageUrl: String
        get() = "https://firebasestorage.googleapis.com/v0/b/$bucketName/o/"

    @Throws(IOException::class)
    fun uploadFile(part: MultipartFile) {
        val inputStream = part.inputStream
        val fileName = part.originalFilename
        val storage = getStorageInstance()
        val bucket = storage[bucketName] ?: throw RuntimeException("Bucket not found: $bucketName")
        bucket.create(fileName, inputStream, "image/jpeg")
    }

    @Throws(IOException::class)
    fun uploadFileAndGetUrl(part: MultipartFile): String {
        val inputStream = part.inputStream
        val fileName = part.originalFilename
        val storage = getStorageInstance()
        val bucket = storage[bucketName] ?: throw RuntimeException("Bucket not found: $bucketName")
        bucket.create(fileName, inputStream, "image/jpeg")

        return "$imageUrl${fileName?.let { java.net.URLEncoder.encode(it, "UTF-8") }}?alt=media"
    }

    @Throws(IOException::class)
    fun uploadFileToFolder(part: MultipartFile, folderName: String): String {
        val inputStream = part.inputStream
        val originalFileName = part.originalFilename
        val timestamp = System.currentTimeMillis()
        val fileName = "${folderName}/${timestamp}_${originalFileName}"
        
        val storage = getStorageInstance()
        val bucket = storage[bucketName] ?: throw RuntimeException("Bucket not found: $bucketName")
        bucket.create(fileName, inputStream, "image/jpeg")

        return "$imageUrl${java.net.URLEncoder.encode(fileName, "UTF-8")}?alt=media"
    }

    @Throws(IOException::class)
    fun uploadBytesToFolder(bytes: ByteArray, originalFileName: String, folderName: String, contentType: String = "image/jpeg"): String {
        // Sube bytes de una imagen a una carpeta de Firebase Storage y devuelve la URL publica.
        val timestamp = System.currentTimeMillis()
        val safeFileName = originalFileName.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val fileName = "${folderName}/${timestamp}_${safeFileName}"

        val storage = getStorageInstance()
        val bucket = storage[bucketName] ?: throw RuntimeException("Bucket not found: $bucketName")
        bucket.create(fileName, bytes, contentType)

        return "$imageUrl${java.net.URLEncoder.encode(fileName, "UTF-8")}?alt=media"
    }

    private fun getStorageInstance(): Storage {
        val resourceAsStream: InputStream = firebaseProperties.serviceAccount?.inputStream
            ?: throw RuntimeException("Firebase service account not configured")
        val credentials = GoogleCredentials.fromStream(resourceAsStream)
        return StorageOptions.newBuilder().setCredentials(credentials).build().service
    }
}
