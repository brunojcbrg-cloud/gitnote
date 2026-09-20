package io.github.wiiznokes.gitnote.ui.viewmodel

/**
 * Fase C do handoff 10: qual pasta a grade abre quando o app inicia. Extraida do
 * [GridViewModel] porque a construcao do VM real passa por [io.github.wiiznokes.gitnote.MyApp.appModule]
 * (GitManager/StorageManager concretos, carregam lib nativa) e nao roda em teste JVM
 * (ver ESTADO_10.md, divergencia da Fase A) — a decisao em si e pura e testavel sozinha.
 */
object PastaInicial {

    /**
     * @return a pasta relativa a abrir. "" e a raiz.
     */
    fun escolher(
        rememberLastOpenedFolder: Boolean,
        lastOpenedFolder: String,
        pastaPadrao: String,
    ): String {
        return when {
            rememberLastOpenedFolder && lastOpenedFolder != "" -> lastOpenedFolder
            pastaPadrao != "" -> pastaPadrao
            else -> ""
        }
    }
}
