package io.github.wiiznokes.gitnote.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Called inside one DataStore edit transaction after the envelope is unlocked. */
class MigracaoDeCredenciais(private val cofre: CofreDeCredenciais) {
    companion object {
        val concluida = booleanPreferencesKey("credenciaisCifradas")
        val envelope = stringPreferencesKey("chaveDeDadosEnvelope")
        val tokenAntigo = stringPreferencesKey("appAuthToken")
        val privateKeyAntiga = stringPreferencesKey("privateKey")
        val passphraseAntiga = stringPreferencesKey("passphrase")
        val senhaAntiga = stringPreferencesKey("userPassPassword")
        val privateKeyCifrada = stringPreferencesKey("privateKeyCifrada")
        val passphraseCifrada = stringPreferencesKey("passphraseCifrada")
        val senhaCifrada = stringPreferencesKey("userPassPasswordCifrada")
        val tokenCifrado = stringPreferencesKey("appAuthTokenCifrado")
    }

    fun migrar(
        prefs: MutablePreferences,
        aposGravarNovo: () -> Unit = {}
    ): Int {
        if (prefs[concluida] == true) return 0
        var total = 0
        for ((antiga, nova) in listOf(
            privateKeyAntiga to privateKeyCifrada,
            passphraseAntiga to passphraseCifrada,
            senhaAntiga to senhaCifrada
        )) {
            val valor = prefs[antiga] ?: continue
            if (prefs[nova] == null) prefs[nova] = cofre.cifrar(valor)
            total++
            aposGravarNovo()
            prefs.remove(antiga)
        }
        // The account-wide OAuth token is never needed after setup.
        prefs.remove(tokenAntigo)
        prefs.remove(tokenCifrado)
        prefs[concluida] = true
        return total
    }
}
