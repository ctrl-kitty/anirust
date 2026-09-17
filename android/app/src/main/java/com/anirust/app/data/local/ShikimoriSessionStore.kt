package com.anirust.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.anirust.app.data.remote.shikimori.ShikimoriRate
import com.anirust.app.data.remote.shikimori.ShikimoriUser
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@JsonClass(generateAdapter = true)
data class ShikimoriOAuthConfig(
    val clientId: String,
    val clientSecret: String,
    val appName: String,
) {
    override fun toString() = "ShikimoriOAuthConfig([redacted])"
}

@JsonClass(generateAdapter = true)
data class ShikimoriSession(
    val config: ShikimoriOAuthConfig,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val user: ShikimoriUser,
    val rates: List<ShikimoriRate> = emptyList(),
    val lastSync: Long = 0,
) {
    override fun toString() = "ShikimoriSession([redacted])"
}

interface ShikimoriSessionStore {
    fun read(): ShikimoriSession?

    fun save(session: ShikimoriSession)

    fun clear()
}

/** Personal tokens are encrypted and excluded from Android backup. */
class EncryptedShikimoriSessionStore(
    context: Context,
    moshi: Moshi,
    private val keyProvider: () -> SecretKey = { androidKey() },
) : ShikimoriSessionStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "shikimori-session.enc"))
    private val adapter = moshi.adapter(ShikimoriSession::class.java)

    override fun read(): ShikimoriSession? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val bytes = file.readFully()
        require(bytes.size > 29 && bytes[0] == 1.toByte()) { "Unsupported session format" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyProvider(),
            GCMParameterSpec(128, bytes.copyOfRange(1, 13)),
        )
        return requireNotNull(
            adapter.fromJson(
                String(cipher.doFinal(bytes.copyOfRange(13, bytes.size)), Charsets.UTF_8)
            )
        )
    }

    override fun save(session: ShikimoriSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val bytes =
            byteArrayOf(1) +
                cipher.iv +
                cipher.doFinal(adapter.toJson(session).toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    override fun clear() {
        file.delete()
    }

    companion object {
        private const val ALIAS = "anirust.shikimori.session.v1"

        @Synchronized
        private fun androidKey(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(ALIAS, null) as? SecretKey)?.let {
                return it
            }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                ALIAS,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setRandomizedEncryptionRequired(true)
                            .build()
                    )
                }
                .generateKey()
        }
    }
}
