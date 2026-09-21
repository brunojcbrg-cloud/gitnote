package io.github.wiiznokes.gitnote.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Android boundary for the JVM-testable envelope. One authenticated cipher is consumed per unlock. */
class AndroidChaveMestra : ChaveMestra {
    companion object {
        private const val ALIAS = "gitnote-credential-master-v1"
    }

    private var autenticado: Cipher? = null

    fun preparar(envelope: String?): Cipher {
        autenticado = null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (envelope == null) {
            cipher.init(Cipher.ENCRYPT_MODE, chave())
        } else {
            val bytes = try {
                Base64.getDecoder().decode(envelope)
            } catch (error: IllegalArgumentException) {
                throw CofreInvalidoException(error)
            }
            if (bytes.size < 28) throw CofreInvalidoException(IllegalArgumentException("Envelope too short"))
            cipher.init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        return cipher
    }

    fun confirmar(cipher: Cipher) {
        autenticado = cipher
    }

    fun apagar() {
        autenticado = null
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
    }

    override fun abrir(envelope: ByteArray): ByteArray {
        val cipher = consumir()
        require(envelope.size >= 28)
        return cipher.doFinal(envelope.copyOfRange(12, envelope.size))
    }

    override fun selar(chave: ByteArray): ByteArray {
        val cipher = consumir()
        return cipher.iv + cipher.doFinal(chave)
    }

    private fun consumir(): Cipher = autenticado?.also { autenticado = null }
        ?: throw CofreBloqueadoException()

    private fun chave(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
                .build()
        )
        return generator.generateKey()
    }
}
