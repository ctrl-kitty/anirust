package com.anirust.app

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anirust.app.data.local.EncryptedShikimoriSessionStore
import com.squareup.moshi.Moshi
import java.io.File
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ShikimoriSessionStoreTest {
    @Test
    fun secretsAreEncryptedAndExcludedFromBackup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val store = EncryptedShikimoriSessionStore(context, Moshi.Builder().build()) { key }
        val session = shikimoriTestSession()
        store.save(session)
        val file = File(context.noBackupFilesDir, "shikimori-session.enc")
        val bytes = file.readBytes()
        assertFalse(String(bytes).contains("test-secret"))
        assertFalse(String(bytes).contains("old-refresh"))
        assertEquals(session, store.read())
        store.save(session)
        assertFalse(bytes.contentEquals(file.readBytes())) // Fresh IV on every save.
        store.clear()
        assertNull(store.read())
        assertFalse(file.exists())
    }

    @Test
    fun modifiedCiphertextIsRejected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val store = EncryptedShikimoriSessionStore(context, Moshi.Builder().build()) { key }
        store.save(shikimoriTestSession())
        val file = File(context.noBackupFilesDir, "shikimori-session.enc")
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertThrows(AEADBadTagException::class.java) { store.read() }
        store.clear()
    }
}
