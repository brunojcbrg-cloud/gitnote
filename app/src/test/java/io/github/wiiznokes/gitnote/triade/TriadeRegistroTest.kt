package io.github.wiiznokes.gitnote.triade

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun clienteJson(nome: String, estado: String): String = """
    {
      "estado": "$estado",
      "onde": "$nome",
      "versao": ${if (estado == "feito") "\"$nome abc\"" else "\"\""},
      "data": "2026-09-27",
      "nota": ${if (estado == "nao_se_aplica") "\"Motivo medido.\"" else "\"nota $nome\""}
    }
""".trimIndent()

private fun funcaoJson(id: String, pc: String, celular: String, web: String): String = """
    {
      "id": "$id",
      "titulo": "Função $id",
      "descricao": "Descrição $id",
      "origem": "HANDOFF.md §4",
      "clientes": {
        "pc": ${clienteJson("pc", pc)},
        "celular": ${clienteJson("celular", celular)},
        "web": ${clienteJson("web", web)}
      }
    }
""".trimIndent()

// a: falta no celular (parcial); b: falta no pc (pendente) e no web (nao_se_aplica nao conta
// como falta, so pendente/parcial/nao_verificado contam); c: falta no celular (nao_verificado).
private fun registroJson(funcoes: String = REGISTRO_PADRAO_FUNCOES): String = """
    {
      "versao": 1,
      "atualizadoEm": "2026-09-27T12:40:00-03:00",
      "funcoes": [$funcoes]
    }
""".trimIndent()

private val REGISTRO_PADRAO_FUNCOES = listOf(
    funcaoJson("a", "feito", "parcial", "feito"),
    funcaoJson("b", "pendente", "feito", "nao_se_aplica"),
    funcaoJson("c", "feito", "nao_verificado", "feito"),
).joinToString(",")

class TriadeRegistroTest {

    @Test
    fun `registro valido vira pronto com as tres funcoes`() {
        val estado = analisarRegistroTriade(registroJson())
        val pronto = assertIs<EstadoRegistroTriade.Pronto>(estado)
        assertEquals(listOf("a", "b", "c"), pronto.registro.funcoes.map { it.id })
    }

    @Test
    fun `versao diferente de 1 e invalida`() {
        val json = registroJson().replace("\"versao\": 1", "\"versao\": 2")
        assertIs<EstadoRegistroTriade.Invalido>(analisarRegistroTriade(json))
    }

    @Test
    fun `ids duplicados sao invalidos`() {
        val duplicado = registroJson(funcoes = "${funcaoJson("a", "feito", "feito", "feito")},${funcaoJson("a", "feito", "feito", "feito")}")
        assertIs<EstadoRegistroTriade.Invalido>(analisarRegistroTriade(duplicado))
    }

    @Test
    fun `feito sem versao medida e invalido`() {
        val semVersao = registroJson().replaceFirst("\"pc abc\"", "\"\"")
        assertIs<EstadoRegistroTriade.Invalido>(analisarRegistroTriade(semVersao))
    }

    @Test
    fun `nao_se_aplica sem motivo e invalido`() {
        val semMotivo = registroJson().replaceFirst("\"Motivo medido.\"", "\"\"")
        assertIs<EstadoRegistroTriade.Invalido>(analisarRegistroTriade(semMotivo))
    }

    @Test
    fun `json corrompido e invalido, nao quebra`() {
        assertIs<EstadoRegistroTriade.Invalido>(analisarRegistroTriade("{isso nao e json"))
    }

    @Test
    fun `arquivo ausente vira Ausente, arquivo valido vira Pronto`() {
        val pasta = kotlin.io.path.createTempDirectory("triade-test").toFile()
        try {
            val inexistente = File(pasta, "funcoes.json")
            assertIs<EstadoRegistroTriade.Ausente>(lerRegistroTriade(inexistente))

            val valido = File(pasta, "valido.json").apply { writeText(registroJson()) }
            assertIs<EstadoRegistroTriade.Pronto>(lerRegistroTriade(valido))
        } finally {
            pasta.deleteRecursively()
        }
    }

    @Test
    fun `filtro por cliente reproduz exatamente a lacuna de cada um`() {
        val registro = (analisarRegistroTriade(registroJson()) as EstadoRegistroTriade.Pronto).registro

        // celular: a=parcial (falta), b=feito (nao falta), c=nao_verificado (falta) -> a, c
        assertEquals(listOf("a", "c"), filtrarFuncoesTriade(registro.funcoes, ClienteTriade.celular, false).map { it.id })
        // web: a=feito, b=nao_se_aplica, c=feito -> nenhuma falta
        assertTrue(filtrarFuncoesTriade(registro.funcoes, ClienteTriade.web, false).isEmpty())
        // pc: a=feito, b=pendente (falta), c=feito -> so b
        assertEquals(listOf("b"), filtrarFuncoesTriade(registro.funcoes, ClienteTriade.pc, false).map { it.id })
    }

    @Test
    fun `filtro so pendentes olha os tres clientes da funcao`() {
        val registro = (analisarRegistroTriade(registroJson()) as EstadoRegistroTriade.Pronto).registro
        // so a funcao b tem algum cliente em "pendente" (o pc dela).
        assertEquals(listOf("b"), filtrarFuncoesTriade(registro.funcoes, cliente = null, soPendentes = true).map { it.id })
    }

    @Test
    fun `filtro sem cliente conta a lacuna em qualquer um dos tres`() {
        val registro = (analisarRegistroTriade(registroJson()) as EstadoRegistroTriade.Pronto).registro
        // "todos": falta em pelo menos um cliente -> a (celular), b (pc), c (celular)
        assertEquals(listOf("a", "b", "c"), filtrarFuncoesTriade(registro.funcoes, cliente = null, soPendentes = false).map { it.id })
    }

    @Test
    fun `contagem do filtro bate com a contagem manual no proprio json`() {
        // Carga maior e sintetica: confere a contagem do filtro contra uma
        // contagem feita na mao sobre a mesma lista, como o handoff pede.
        val estados = listOf("feito", "parcial", "pendente", "nao_se_aplica", "nao_verificado")
        val funcoes = estados.indices.map { i ->
            funcaoJson(
                id = "f$i",
                pc = estados[i],
                celular = estados[(i + 1) % estados.size],
                web = estados[(i + 2) % estados.size],
            )
        }.joinToString(",")
        val registro = (analisarRegistroTriade(registroJson(funcoes)) as EstadoRegistroTriade.Pronto).registro

        val esperadoCelular = registro.funcoes.count {
            val e = it.clientes.getValue(ClienteTriade.celular).estado
            e != EstadoTriade.feito && e != EstadoTriade.nao_se_aplica
        }
        assertEquals(esperadoCelular, filtrarFuncoesTriade(registro.funcoes, ClienteTriade.celular, false).size)
    }
}
