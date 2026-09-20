package io.github.wiiznokes.gitnote.data

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Only the implementation of this interface knows about AndroidKeyStore. */
interface ChaveMestra {
    fun abrir(envelope: ByteArray): ByteArray
    fun selar(chave: ByteArray): ByteArray
}

class CofreInvalidoException(cause: Throwable) : Exception("Credencial cifrada inválida", cause)
class CofreBloqueadoException : IllegalStateException("O cofre de credenciais está bloqueado")

/** AES-GCM data envelope; all methods here run on an ordinary JVM. */
class CofreDeCredenciais(private val mestra: ChaveMestra) {
    private val random = SecureRandom()
    private var chaveDeDados: ByteArray? = null

    val aberto: Boolean get() = chaveDeDados != null

    fun criarEnvelope(): String {
        val chave = ByteArray(32).also(random::nextBytes)
        try {
            val envelope = mestra.selar(chave)
            bloquear()
            chaveDeDados = chave
            return Base64.getEncoder().encodeToString(envelope)
        } catch (error: Exception) {
            chave.fill(0)
            throw error
        }
    }

    fun abrirEnvelope(envelope: String) {
        try {
            val chave = mestra.abrir(Base64.getDecoder().decode(envelope))
            if (chave.size != 32) {
                chave.fill(0)
                throw IllegalArgumentException("Invalid data key size")
            }
            bloquear()
            chaveDeDados = chave
        } catch (error: Exception) {
            throw CofreInvalidoException(error)
        }
    }

    fun cifrar(valor: String): String {
        val chave = chaveDeDados ?: throw CofreBloqueadoException()
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(chave, "AES"), GCMParameterSpec(128, iv))
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(valor.toByteArray(Charsets.UTF_8)))
    }

    fun decifrar(valor: String): String {
        val chave = chaveDeDados ?: throw CofreBloqueadoException()
        try {
            val bytes = Base64.getDecoder().decode(valor)
            require(bytes.size >= 12 + 16) { "Ciphertext too short" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(chave, "AES"),
                GCMParameterSpec(128, bytes.copyOfRange(0, 12))
            )
            return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        } catch (error: IllegalArgumentException) {
            throw CofreInvalidoException(error)
        } catch (error: GeneralSecurityException) {
            throw CofreInvalidoException(error)
        }
    }

    fun bloquear() {
        chaveDeDados?.fill(0)
        chaveDeDados = null
    }
}
