package io.github.wiiznokes.gitnote.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MigracaoDeCredenciaisTest {
    private fun cofre() = CofreDeCredenciais(MestraDeTeste()).also { it.criarEnvelope() }

    @Test
    fun antigasSomemENovasAbremComValoresExatos() {
        val cofre = cofre()
        val prefs = mutablePreferencesOf(
            MigracaoDeCredenciais.privateKeyAntiga to "chave\nprivada",
            MigracaoDeCredenciais.passphraseAntiga to "ação",
            MigracaoDeCredenciais.senhaAntiga to "senha"
        )
        assertEquals(3, MigracaoDeCredenciais(cofre).migrar(prefs))
        assertNull(prefs[MigracaoDeCredenciais.privateKeyAntiga])
        assertNull(prefs[MigracaoDeCredenciais.passphraseAntiga])
        assertNull(prefs[MigracaoDeCredenciais.senhaAntiga])
        assertEquals("chave\nprivada", cofre.decifrar(prefs[MigracaoDeCredenciais.privateKeyCifrada]!!))
        assertEquals("ação", cofre.decifrar(prefs[MigracaoDeCredenciais.passphraseCifrada]!!))
        assertEquals("senha", cofre.decifrar(prefs[MigracaoDeCredenciais.senhaCifrada]!!))
        assertTrue(prefs[MigracaoDeCredenciais.concluida] == true)
    }

    @Test
    fun tokenOauthAntigoEAteCifradoSaoApagados() {
        val prefs = mutablePreferencesOf(
            MigracaoDeCredenciais.tokenAntigo to "token-antigo",
            MigracaoDeCredenciais.tokenCifrado to "token-cifrado"
        )
        assertEquals(0, MigracaoDeCredenciais(cofre()).migrar(prefs))
        assertNull(prefs[MigracaoDeCredenciais.tokenAntigo])
        assertNull(prefs[MigracaoDeCredenciais.tokenCifrado])
    }

    @Test
    fun migracaoDuasVezesNaoRecifra() {
        val cofre = cofre()
        val prefs = mutablePreferencesOf(MigracaoDeCredenciais.privateKeyAntiga to "chave")
        val migracao = MigracaoDeCredenciais(cofre)
        assertEquals(1, migracao.migrar(prefs))
        val cifrado = prefs[MigracaoDeCredenciais.privateKeyCifrada]
        assertEquals(0, migracao.migrar(prefs))
        assertEquals(cifrado, prefs[MigracaoDeCredenciais.privateKeyCifrada])
    }

    @Test
    fun falhaEntreGravarNovaERemoverAntigaNaoPerdeCredencial() {
        val cofre = cofre()
        val prefs = mutablePreferencesOf(MigracaoDeCredenciais.privateKeyAntiga to "chave")
        val migracao = MigracaoDeCredenciais(cofre)
        assertFailsWith<IllegalStateException> {
            migracao.migrar(prefs) { throw IllegalStateException("interrupção") }
        }
        assertEquals("chave", prefs[MigracaoDeCredenciais.privateKeyAntiga])
        assertEquals(1, migracao.migrar(prefs))
        assertNull(prefs[MigracaoDeCredenciais.privateKeyAntiga])
        assertEquals("chave", cofre.decifrar(prefs[MigracaoDeCredenciais.privateKeyCifrada]!!))
    }
}
