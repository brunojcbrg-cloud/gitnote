package io.github.wiiznokes.gitnote.data

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class MestraDeTeste(private val chave: ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)) : ChaveMestra {
    override fun selar(chave: ByteArray): ByteArray {
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(this.chave, "AES"), GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(chave)
    }

    override fun abrir(envelope: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(chave, "AES"),
            GCMParameterSpec(128, envelope.copyOfRange(0, 12))
        )
        return cipher.doFinal(envelope.copyOfRange(12, envelope.size))
    }
}

class CofreDeCredenciaisTest {
    @Test
    fun idaEVoltaIncluiVazioAcentoEChaveSshComQuebras() {
        val cofre = CofreDeCredenciais(MestraDeTeste())
        val envelope = cofre.criarEnvelope()
        val chaveSsh = """-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtz
c2gtZWQyNTUxOQAAACDb6R9rX3zHZn1So6I+kONxtA6llkn8pbFEc6dkG2PDGQ==
-----END OPENSSH PRIVATE KEY-----"""
        listOf("", "São João — ação", chaveSsh).forEach { valor ->
            assertEquals(valor, cofre.decifrar(cofre.cifrar(valor)))
        }
        cofre.bloquear()
        cofre.abrirEnvelope(envelope)
        assertEquals(chaveSsh, cofre.decifrar(cofre.cifrar(chaveSsh)))
    }

    @Test
    fun ivNovoParaCadaValorMesmoQuandoTextoIgual() {
        val cofre = CofreDeCredenciais(MestraDeTeste())
        cofre.criarEnvelope()
        assertNotEquals(cofre.cifrar("mesmo valor"), cofre.cifrar("mesmo valor"))
    }

    @Test
    fun adulteracaoDoCiphertextFalhaPelaTagGcm() {
        val cofre = CofreDeCredenciais(MestraDeTeste())
        cofre.criarEnvelope()
        val bytes = Base64.getDecoder().decode(cofre.cifrar("segredo"))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertFailsWith<CofreInvalidoException> {
            cofre.decifrar(Base64.getEncoder().encodeToString(bytes))
        }
    }

    @Test
    fun envelopeDeUmaMestraNaoAbreComOutra() {
        val envelope = CofreDeCredenciais(MestraDeTeste()).criarEnvelope()
        assertFailsWith<CofreInvalidoException> {
            CofreDeCredenciais(MestraDeTeste()).abrirEnvelope(envelope)
        }
    }

    @Test
    fun base64MalformadoViraErroTratado() {
        val cofre = CofreDeCredenciais(MestraDeTeste())
        cofre.criarEnvelope()
        val error = assertFailsWith<CofreInvalidoException> { cofre.decifrar("%%%") }
        assertTrue(error.message!!.contains("inválida"))
    }
}
