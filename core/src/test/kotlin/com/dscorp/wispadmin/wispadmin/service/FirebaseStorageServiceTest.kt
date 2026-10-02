package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.firebase.FirebaseProperties
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ByteArrayResource
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.util.ReflectionTestUtils
import java.io.InputStream

class FirebaseStorageServiceTest {
    @AfterEach
    fun clearStaticMocks() {
        unmockkAll()
    }

    @Test
    fun `uploadFileToFolder writes directly to the configured bucket`() {
        val storage = mockk<Storage>()
        val storageOptions = mockk<StorageOptions>()
        val storageOptionsBuilder = mockk<StorageOptions.Builder>()
        val credentials = mockk<GoogleCredentials>()
        val blobInfo = slot<BlobInfo>()
        val properties = FirebaseProperties().apply {
            serviceAccount = ByteArrayResource(byteArrayOf(1, 2, 3))
        }
        val service = FirebaseStorageService(properties)
        ReflectionTestUtils.setField(service, "bucketName", "lab-bucket")
        val part = MockMultipartFile(
            "facadePhoto",
            "facade.jpg",
            "image/jpeg",
            byteArrayOf(1, 2, 3),
        )

        mockkStatic(GoogleCredentials::class)
        mockkStatic(StorageOptions::class)
        every { GoogleCredentials.fromStream(any<InputStream>()) } returns credentials
        every { StorageOptions.newBuilder() } returns storageOptionsBuilder
        every { storageOptionsBuilder.setCredentials(credentials) } returns storageOptionsBuilder
        every { storageOptionsBuilder.build() } returns storageOptions
        every { storageOptions.service } returns storage
        every {
            storage.create(capture(blobInfo), any<InputStream>())
        } returns mockk<Blob>()

        val url = service.uploadFileToFolder(part, "registration")

        assertEquals("lab-bucket", blobInfo.captured.bucket)
        assertTrue(blobInfo.captured.name.startsWith("registration/"))
        assertEquals("image/jpeg", blobInfo.captured.contentType)
        assertTrue(url.startsWith("https://firebasestorage.googleapis.com/v0/b/lab-bucket/o/"))
        verify(exactly = 0) { storage.get(any<String>()) }
    }
}
