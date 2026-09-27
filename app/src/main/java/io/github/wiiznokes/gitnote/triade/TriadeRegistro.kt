package io.github.wiiznokes.gitnote.triade

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Leitura do registro único da tríade (`05_Sistema/triade/funcoes.json`), o
 * mesmo arquivo que o Life SO do PC e o notas-web leem -- aqui pelo clone do
 * vault, não pela API do GitHub. Regras de validação espelham
 * `notas-web/src/triade.ts` (`analisarRegistroTriade`, `faltaNoCliente`,
 * `filtrarFuncoesTriade`): mesmo contrato, para os três clientes concordarem
 * sobre o que está "feito".
 */
const val CAMINHO_TRIADE_NO_VAULT = "05_Sistema/triade/funcoes.json"

enum class EstadoTriade {
    feito, parcial, pendente, nao_se_aplica, nao_verificado
}

enum class ClienteTriade { pc, celular, web }

@Serializable
private data class ClienteFuncaoTriadeBruto(
    val estado: String? = null,
    val onde: String? = null,
    val versao: String? = null,
    val data: String? = null,
    val nota: String? = null,
)

@Serializable
private data class ClientesTriadeBruto(
    val pc: ClienteFuncaoTriadeBruto? = null,
    val celular: ClienteFuncaoTriadeBruto? = null,
    val web: ClienteFuncaoTriadeBruto? = null,
)

@Serializable
private data class FuncaoTriadeBruta(
    val id: String? = null,
    val titulo: String? = null,
    val descricao: String? = null,
    val origem: String? = null,
    val clientes: ClientesTriadeBruto? = null,
)

@Serializable
private data class RegistroTriadeBruto(
    val versao: Int? = null,
    val atualizadoEm: String? = null,
    val funcoes: List<FuncaoTriadeBruta> = emptyList(),
)

data class ClienteFuncaoTriade(
    val estado: EstadoTriade,
    val onde: String,
    val versao: String,
    val data: String,
    val nota: String,
)

data class FuncaoTriade(
    val id: String,
    val titulo: String,
    val descricao: String,
    val origem: String,
    val clientes: Map<ClienteTriade, ClienteFuncaoTriade>,
)

data class RegistroTriade(
    val versao: Int,
    val atualizadoEm: String,
    val funcoes: List<FuncaoTriade>,
)

sealed interface EstadoRegistroTriade {
    data class Pronto(val registro: RegistroTriade) : EstadoRegistroTriade
    data class Ausente(val mensagem: String) : EstadoRegistroTriade
    data class Invalido(val mensagem: String) : EstadoRegistroTriade
    data class Indisponivel(val mensagem: String) : EstadoRegistroTriade
}

private const val MENSAGEM_AUSENTE = "Registro da tríade ainda não foi sincronizado."
private const val MENSAGEM_INVALIDO = "Registro da tríade inválido. Rode o verificador no PC e sincronize o vault."
private const val MENSAGEM_INDISPONIVEL = "Não foi possível ler a tríade agora. As notas continuam disponíveis."

private val jsonLaxo = Json { ignoreUnknownKeys = true }
private val padraoData = Regex("^\\d{4}-\\d{2}-\\d{2}$")

private fun clienteValido(bruto: ClienteFuncaoTriadeBruto?): Boolean {
    if (bruto == null || bruto.onde == null || bruto.versao == null || bruto.data == null || bruto.nota == null) {
        return false
    }
    val estado = runCatching { EstadoTriade.valueOf(bruto.estado.orEmpty()) }.getOrNull() ?: return false
    return when (estado) {
        EstadoTriade.feito -> bruto.versao.isNotBlank()
        EstadoTriade.nao_se_aplica -> bruto.nota.isNotBlank()
        EstadoTriade.pendente -> padraoData.matches(bruto.data)
        EstadoTriade.parcial, EstadoTriade.nao_verificado -> true
    }
}

private fun ClienteFuncaoTriadeBruto.paraDominio() = ClienteFuncaoTriade(
    estado = EstadoTriade.valueOf(requireNotNull(estado)),
    onde = onde.orEmpty(),
    versao = versao.orEmpty(),
    data = data.orEmpty(),
    nota = nota.orEmpty(),
)

private fun funcaoValida(bruta: FuncaoTriadeBruta): Boolean {
    if (bruta.id.isNullOrBlank() || bruta.titulo.isNullOrBlank() ||
        bruta.descricao.isNullOrBlank() || bruta.origem.isNullOrBlank()
    ) return false
    val clientes = bruta.clientes ?: return false
    return clienteValido(clientes.pc) && clienteValido(clientes.celular) && clienteValido(clientes.web)
}

fun analisarRegistroTriade(json: String): EstadoRegistroTriade {
    val bruto = runCatching { jsonLaxo.decodeFromString<RegistroTriadeBruto>(json) }.getOrNull()
        ?: return EstadoRegistroTriade.Invalido(MENSAGEM_INVALIDO)
    if (bruto.versao != 1 || bruto.atualizadoEm.isNullOrBlank() || !bruto.funcoes.all(::funcaoValida)) {
        return EstadoRegistroTriade.Invalido(MENSAGEM_INVALIDO)
    }
    val ids = bruto.funcoes.map { it.id }
    if (ids.toSet().size != ids.size) return EstadoRegistroTriade.Invalido(MENSAGEM_INVALIDO)

    val funcoes = bruto.funcoes.map { funcao ->
        val clientes = requireNotNull(funcao.clientes)
        FuncaoTriade(
            id = requireNotNull(funcao.id),
            titulo = requireNotNull(funcao.titulo),
            descricao = requireNotNull(funcao.descricao),
            origem = requireNotNull(funcao.origem),
            clientes = mapOf(
                ClienteTriade.pc to requireNotNull(clientes.pc).paraDominio(),
                ClienteTriade.celular to requireNotNull(clientes.celular).paraDominio(),
                ClienteTriade.web to requireNotNull(clientes.web).paraDominio(),
            ),
        )
    }
    return EstadoRegistroTriade.Pronto(RegistroTriade(bruto.versao, bruto.atualizadoEm, funcoes))
}

/** [arquivo]: `<clone do vault>/05_Sistema/triade/funcoes.json`. */
fun lerRegistroTriade(arquivo: File): EstadoRegistroTriade {
    if (!arquivo.isFile) return EstadoRegistroTriade.Ausente(MENSAGEM_AUSENTE)
    val texto = runCatching { arquivo.readText() }.getOrNull()
        ?: return EstadoRegistroTriade.Indisponivel(MENSAGEM_INDISPONIVEL)
    return analisarRegistroTriade(texto)
}

fun faltaNoCliente(funcao: FuncaoTriade, cliente: ClienteTriade): Boolean {
    val estado = funcao.clientes.getValue(cliente).estado
    return estado != EstadoTriade.feito && estado != EstadoTriade.nao_se_aplica
}

/** [cliente] nulo == "todos" (mesmo sentido do filtro do Life SO e da web). */
fun filtrarFuncoesTriade(
    funcoes: List<FuncaoTriade>,
    cliente: ClienteTriade?,
    soPendentes: Boolean,
): List<FuncaoTriade> {
    val clientesConsiderados = cliente?.let { listOf(it) } ?: ClienteTriade.entries
    return funcoes.filter { funcao ->
        if (soPendentes) {
            clientesConsiderados.any { funcao.clientes.getValue(it).estado == EstadoTriade.pendente }
        } else {
            cliente == null || faltaNoCliente(funcao, cliente)
        }
    }
}
