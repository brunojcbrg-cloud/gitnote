package io.github.wiiznokes.gitnote.data

import android.content.Context
import android.util.Log
import io.github.wiiznokes.gitnote.manager.PreferencesManager

/**
 * Onde o usuario parou em cada nota, por linha.
 *
 * A linha e a mesma ancora que a tela ja usa para trocar entre leitura e edicao
 * (`rememberAnchor` / `consumeAnchor`). Guardar LINHA, e nao pixel, e o que faz a
 * retomada continuar certa depois de a nota ser editada, a fonte mudar de tamanho
 * ou o telefone girar -- um deslocamento em pixel apontaria para outro lugar.
 *
 * Toda a logica vive aqui, sem Android, para poder ser testada na JVM.
 */
object RegistroDePosicoes {

    /** Quantas notas ficam lembradas. 139 notas no vault dele hoje; 300 da folga. */
    const val LIMITE = 300

    private const val SEPARADOR = '\t'

    /**
     * Decodifica o registro. Entrada corrompida devolve o que der para aproveitar:
     * perder a posicao de uma nota e um aborrecimento, lancar excecao ao abrir a
     * nota e um defeito.
     */
    fun decodificar(bruto: String): LinkedHashMap<String, Int> {
        val mapa = LinkedHashMap<String, Int>()
        if (bruto.isEmpty()) return mapa
        bruto.lineSequence().forEach { linha ->
            if (linha.isBlank()) return@forEach
            val corte = linha.lastIndexOf(SEPARADOR)
            if (corte <= 0) return@forEach
            val caminho = linha.substring(0, corte)
            val numero = linha.substring(corte + 1).toIntOrNull() ?: return@forEach
            if (numero < 0) return@forEach
            if (caminho !in mapa) mapa[caminho] = numero
        }
        return mapa
    }

    fun codificar(mapa: Map<String, Int>): String =
        mapa.entries.joinToString("\n") { (caminho, linha) -> "$caminho$SEPARADOR$linha" }

    fun posicao(bruto: String, caminho: String): Int? =
        if (caminho.isBlank()) null else decodificar(bruto)[caminho]

    /**
     * Devolve o registro com [caminho] na frente. A ordem e de uso, nao alfabetica:
     * quando o limite estoura, quem cai e a nota aberta ha mais tempo.
     */
    fun registrar(
        bruto: String,
        caminho: String,
        linha: Int,
        limite: Int = LIMITE,
    ): String {
        // Caminho com separador ou quebra sairia do registro sem volta.
        if (caminho.isBlank() || caminho.contains(SEPARADOR) || caminho.contains('\n')) return bruto

        val anterior = decodificar(bruto)
        anterior.remove(caminho)
        val novo = LinkedHashMap<String, Int>(anterior.size + 1)
        novo[caminho] = linha.coerceAtLeast(0)
        anterior.entries.take((limite - 1).coerceAtLeast(0)).forEach { (chave, valor) ->
            novo[chave] = valor
        }
        return codificar(novo)
    }

    fun esquecer(bruto: String, caminho: String): String {
        val mapa = decodificar(bruto)
        return if (mapa.remove(caminho) == null) bruto else codificar(mapa)
    }
}

/**
 * Decide o que conta como rolagem do usuario enquanto a tela ainda esta abrindo.
 *
 * Ao abrir, a tela anuncia a linha 0 mais de uma vez -- a varredura dos blocos
 * assentando e o primeiro valor da rolagem -- e isso acontece ANTES de a retomada
 * rolar para onde ele parou. Gravar esses zeros apagaria a posicao guardada, que e
 * exatamente o defeito que este recurso existe para consertar.
 *
 * Por isso o portao segura TODO zero ate chegar a primeira linha de verdade, em vez
 * de segurar so o primeiro: segurar um so deixava o seguinte passar.
 */
class PortaoDaAbertura {

    private var segurandoZero = false

    /** Chamado com a linha retomada do disco; nulo ou 0 significa que nao ha o que proteger. */
    fun retomouEm(linha: Int?) {
        segurandoZero = linha != null && linha > 0
    }

    fun deveGravar(linha: Int): Boolean {
        if (segurandoZero) {
            if (linha <= 0) return false
            segurandoZero = false
        }
        return true
    }
}

private const val TAG = "PosicoesDeLeitura"

class PosicoesDeLeitura(context: Context) : PreferencesManager(context, "posicoes-de-leitura") {

    private val registro = stringPreference("registro", "")

    /**
     * Leitura bloqueante, uma vez por nota aberta.
     *
     * A tela pede a ancora dentro de um `remember`, na primeira composicao, entao
     * uma leitura assincrona chegaria tarde e a retomada simplesmente nao
     * aconteceria. O mesmo caminho ja existe em `repoPathBlocking`.
     */
    fun linhaBloqueante(caminho: String): Int? =
        runCatching { RegistroDePosicoes.posicao(registro.getBlocking(), caminho) }
            .onFailure { Log.w(TAG, "leitura bloqueante falhou para $caminho", it) }
            .getOrNull()

    /**
     * Mesma leitura, sem bloquear. E a rede de seguranca do arranque frio: se a
     * leitura bloqueante voltar vazia porque o arquivo ainda nao abriu, esta aqui
     * chega depois e a tela aplica o resultado.
     */
    suspend fun linha(caminho: String): Int? =
        runCatching { RegistroDePosicoes.posicao(registro.get(), caminho) }
            .onFailure { Log.w(TAG, "leitura falhou para $caminho", it) }
            .getOrNull()

    suspend fun guardar(caminho: String, linha: Int) {
        runCatching {
            registro.update(RegistroDePosicoes.registrar(registro.get(), caminho, linha))
        }.onFailure { Log.w(TAG, "nao consegui guardar $caminho na linha $linha", it) }
    }

    suspend fun esquecer(caminho: String) {
        runCatching { registro.update(RegistroDePosicoes.esquecer(registro.get(), caminho)) }
    }
}
