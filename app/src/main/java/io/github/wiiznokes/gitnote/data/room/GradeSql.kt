package io.github.wiiznokes.gitnote.data.room

import io.github.wiiznokes.gitnote.ui.model.SortOrder

/**
 * Fase K.2 do handoff 10: o texto do SQL da grade, fora do DAO.
 *
 * Esta separacao existe por um motivo pratico: o `RepoDatabaseDao` so roda com as
 * funcoes SQLite customizadas do requery (`parentPath`/`fullName`/`rank`/`caseFold`),
 * que nao carregam na JVM de teste (ver ESTADO_10.md, divergencias das Fases A e B).
 * Com a consulta da pasta voltando a ser recursiva (`LIKE`), a listagem da ordem
 * padrao deixou de precisar de qualquer funcao customizada — entao o MESMO texto que
 * vai pro banco em producao pode ser executado num SQLite comum dentro do teste.
 *
 * Duas regras da Fase K estao cravadas aqui e nao podem voltar atras (sao o ganho da
 * Fase B): **nao ha coluna `content` na projecao** e **nao ha funcao de janela**.
 */
internal object GradeSql {

    /**
     * Filtro recursivo: a pasta aberta e todas as descendentes.
     *
     * A **barra** e obrigatoria: sem ela, abrir `Medicina` casaria tambem com
     * `Medicina2/...` e ate com um arquivo `Medicina.md` solto ao lado. Era o defeito
     * latente que a Fase B corrigiu, e a Fase K.2 o mantem corrigido.
     *
     * O mesmo nome (`:currentNoteFolderRelativePath`) aparece duas vezes de proposito:
     * parametro nomeado repetido compartilha o mesmo indice de bind, entao continua
     * sendo **um** argumento — igual ao que `gridNotesWithQuery` ja faz desde a Fase B.
     */
    const val FILTRO_RECURSIVO: String =
        "(:currentNoteFolderRelativePath = '' " +
            "OR relativePath LIKE :currentNoteFolderRelativePath || '/%')"

    /**
     * A listagem da grade. [teto] nulo = sem limite ("Mostrar todas").
     *
     * Quem segura o custo da recursao e o `LIMIT`: a projecao e leve (4 colunas, sem
     * conteudo) e nao ha janela sobre o conjunto inteiro, entao o banco toca poucas
     * linhas na abertura da pasta.
     */
    fun notasDaPasta(sortOrder: SortOrder, teto: Int?): String {

        val (sortColumn, order) = when (sortOrder) {
            // recursiva de novo: ordenar por relativePath agruparia por pasta, entao
            // A-Z/Z-A voltam a ordenar pelo nome do arquivo, como antes da Fase B.
            SortOrder.AZ -> "fullName(relativePath)" to "ASC"
            SortOrder.ZA -> "fullName(relativePath)" to "DESC"
            SortOrder.MostRecent -> "lastModifiedTimeMillis" to "DESC"
            SortOrder.Oldest -> "lastModifiedTimeMillis" to "ASC"
            SortOrder.UltimaVisualizacao ->
                "MAX(lastOpenedTimeMillis, lastModifiedTimeMillis)" to "DESC"
        }

        val limite = if (teto == null) "" else "LIMIT ${teto.coerceAtLeast(0)}"

        return """
            SELECT relativePath, id, lastModifiedTimeMillis, 1 AS isUnique
            FROM Notes
            WHERE $FILTRO_RECURSIVO
            ORDER BY $sortColumn $order, relativePath ASC
            $limite
        """.trimIndent()
    }

    /**
     * O total do rodape "Mostrar todas (N)". Recursivo pelo mesmo motivo que a
     * listagem: desde a Fase K, "todas" quer dizer a pasta **e as descendentes**.
     */
    fun contagemDaPasta(): String =
        "SELECT COUNT(*) FROM Notes WHERE $FILTRO_RECURSIVO"
}
