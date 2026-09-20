package io.github.wiiznokes.gitnote.ui.component.markdown

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.measureTime

class DobraTest {

    @Test
    fun secaoDeNivel2CobreOsNiveis3a6AteAProximaDeNivel2() {
        val texto = listOf(
            "## Seção A",
            "corpo de A",
            "### A.1",
            "corpo de A.1",
            "#### A.1.a",
            "corpo de A.1.a",
            "## Seção B",
            "corpo de B",
        ).joinToString("\n")
        val sumario = sumarioDe(texto)
        val secoes = secoesDe(texto, sumario)

        val secaoA = secoes.first { it.titulo.texto == "Seção A" }
        val ofertaB = sumario.first { it.texto == "Seção B" }.offset
        assertEquals(ofertaB, secaoA.fimCorpo)

        val secaoA1 = secoes.first { it.titulo.texto == "A.1" }
        assertTrue(secaoA1.fimCorpo <= ofertaB)
    }

    @Test
    fun conjuntoVazioDevolveOTextoIdentico() {
        val texto = listOf("# Título", "corpo", "## Sub", "mais corpo").joinToString("\n")
        val resultado = dobrar(texto, emptySet())
        assertEquals(texto, resultado.visivel)
    }

    @Test
    fun recolherNivel2EscondeNiveis3a6EMasNaoAProximaDeNivel2() {
        val texto = listOf(
            "## Seção A",
            "corpo de A",
            "### A.1",
            "corpo de A.1",
            "###### A.1.a.i",
            "corpo profundo",
            "## Seção B",
            "corpo de B visível",
        ).joinToString("\n")
        val sumario = sumarioDe(texto)
        val ofertaA = sumario.first { it.texto == "Seção A" }.offset

        val resultado = dobrar(texto, setOf(ofertaA))

        assertFalse(resultado.visivel.contains("corpo de A.1"))
        assertFalse(resultado.visivel.contains("A.1.a.i"))
        assertFalse(resultado.visivel.contains("corpo profundo"))
        assertTrue(resultado.visivel.contains("Seção A"))
        assertTrue(resultado.visivel.contains("Seção B"))
        assertTrue(resultado.visivel.contains("corpo de B visível"))
    }

    @Test
    fun converterOffsetParaVisivelEDeVoltaDevolveOOriginalParaTitulosVisiveis() {
        val random = Random(20260920)
        repeat(30) { rodada ->
            val niveis = List(40) { random.nextInt(1, 5) }
            val texto = niveis.mapIndexed { indice, nivel ->
                "${"#".repeat(nivel)} Título $indice\ncorpo $indice com algum texto de exemplo"
            }.joinToString("\n")

            val sumario = sumarioDe(texto)
            val k = random.nextInt(1, sumario.size)
            val recolhidas = sumario.shuffled(random).take(k).map { it.offset }.toSet()

            val resultado = dobrar(texto, recolhidas)
            val secoes = secoesDe(texto, sumario)
            val cortes = secoes.filter { it.titulo.offset in recolhidas && it.fimCorpo > it.inicioCorpo }
                .map { it.inicioCorpo to it.fimCorpo }

            fun escondido(offset: Int) = cortes.any { (inicio, fim) -> offset in inicio until fim }

            for (item in sumario) {
                if (escondido(item.offset)) continue
                val visivel = resultado.mapa.paraVisivel(item.offset)
                val original = resultado.mapa.paraOriginal(visivel)
                assertEquals(
                    item.offset,
                    original,
                    "rodada $rodada, título '${item.texto}' offset ${item.offset}",
                )
            }
        }
    }

    @Test
    fun recolherTudoNumaNotaSemAninhamentoDeixaSoAsLinhasDeTitulo() {
        // Nota sintética FLAT (mesmo padrão de SumarioTest.perfSumarioNaNotaDe1946Linhas):
        // todos os títulos no mesmo nível, sem aninhamento. É onde "recolher tudo" (marcar
        // todos os títulos) e "recolher em cascata" (Fase F) coincidem: sem filhos para
        // engolir, cada título sobrevive como sua própria linha.
        val linhas = MutableList(1_946) { index ->
            if (index % 7 == 0) "## Título $index" else "Parágrafo $index com texto de estudo"
        }
        val nota = linhas.joinToString("\n")
        val sumario = sumarioDe(nota)
        val recolhidas = sumario.map { it.offset }.toSet()

        val resultado = dobrar(nota, recolhidas)
        // O último título preserva sua própria quebra de linha (fica antes do corte
        // dele): sobra um "\n" no fim do texto visível, que split() conta como uma
        // linha extra vazia -- removeSuffix tira só essa, não mexe no conteúdo.
        val linhasVisiveis = resultado.visivel.removeSuffix("\n").split("\n")

        assertEquals(sumario.size, linhasVisiveis.size)
        assertEquals(sumario.map { it.texto }, sumario.indices.map {
            sumarioDe(resultado.visivel)[it].texto
        })
    }

    @Test
    fun recolherTudoNaNotaRealMaisAninhadaDeixaSoOsTitulosDeNivel1() {
        // Medido em 20/09/2026 na maior nota real de 06_Conhecimento (Aula Introdução à
        // micro.md): 269 títulos, níveis 1..4 (117/25/79/48). Em cascata (Fase F, "não pode
        // regredir": recolher um título esconde os níveis abaixo dele) recolher todos os 269
        // títulos deixa visíveis só os 117 de nível 1 — os demais estão aninhados dentro de
        // algum deles. Reproduzido aqui em miniatura para não depender do vault no CI.
        val sumarioReal = buildList {
            var i = 0
            repeat(4) { // 4 raízes de nível 1, cada uma com a mesma arvore aninhada
                add(ItemDeSumario(1, "Raiz $i", 0, 0)); i++
                repeat(2) {
                    add(ItemDeSumario(2, "N2 $i", 0, 0)); i++
                    repeat(3) {
                        add(ItemDeSumario(3, "N3 $i", 0, 0)); i++
                        repeat(2) {
                            add(ItemDeSumario(4, "N4 $i", 0, 0)); i++
                        }
                    }
                }
            }
        }
        val texto = sumarioReal.joinToString("\n") { "${"#".repeat(it.nivel)} ${it.texto}\ncorpo de ${it.texto}" }
        val sumario = sumarioDe(texto)
        val nivel1 = sumario.count { it.nivel == 1 }

        val recolhidas = sumario.map { it.offset }.toSet()
        val resultado = dobrar(texto, recolhidas)
        val titulosVisiveis = sumarioDe(resultado.visivel)

        assertEquals(nivel1, titulosVisiveis.size)
        assertTrue(titulosVisiveis.all { it.nivel == 1 })
    }

    @Test
    fun perfDobrarNaNotaDe1946Linhas() {
        val linhas = MutableList(1_946) { index ->
            if (index % 7 == 0) "## Título $index com **ênfase**"
            else "Parágrafo $index com texto de estudo"
        }
        val base = linhas.joinToString("\n")
        linhas[linhas.lastIndex] += "x".repeat(180_046 - base.length)
        val nota = linhas.joinToString("\n")
        val sumario = sumarioDe(nota)
        // Recolhe 1 a cada 3 títulos: caso representativo do painel, não o extremo de tudo.
        val recolhidas = sumario.filterIndexed { indice, _ -> indice % 3 == 0 }.map { it.offset }.toSet()

        repeat(3) { dobrar(nota, recolhidas) }
        val amostras = List(5) {
            measureTime { dobrar(nota, recolhidas) }.inWholeMicroseconds / 1_000.0
        }
        val mediana = amostras.sorted()[2]
        println("PERF_DOBRAR linhas=1946 chars=180046 recolhidas=${recolhidas.size} amostras_ms=$amostras mediana_ms=$mediana")
        assertEquals(180_046, nota.length)
    }
}
