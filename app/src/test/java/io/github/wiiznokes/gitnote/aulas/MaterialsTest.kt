package io.github.wiiznokes.gitnote.aulas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class MaterialsTest {
    @Test
    fun `manifesto v2 aceita html e preserva arvore pelo caminho`() {
        val manifest = parseMaterialsManifest("""
            {"versao":2,"geradoEm":"2026-09-27T23:00:00Z","arquivos":[
              {"id":"drive-1","name":"Aula - 08 - apostila final.html","size":1200,
               "modifiedTime":"2026-09-27T23:00:00Z","caminho":"Genética/P1/Aula/Aula - 08 - apostila final.html","tipo":"html"}
            ]}
        """.trimIndent())
        assertTrue(manifest.arquivos.single().isHtml)
        assertEquals("Genética", manifest.arquivos.single().caminho.substringBefore('/'))
    }

    @Test
    fun `manifesto recusa escape e id invalido`() {
        assertFails {
            parseMaterialsManifest("""
                {"versao":2,"geradoEm":"x","arquivos":[
                  {"id":"id com espaço","name":"A.html","size":1,"modifiedTime":"x","caminho":"../A.html"}
                ]}
            """.trimIndent())
        }
    }

    @Test
    fun `cache remove os mais antigos ate caber e preserva o arquivo aberto`() {
        val removals = cacheEvictions(
            entries = listOf(
                CacheEntry("antigo", 40, 1),
                CacheEntry("novo", 40, 3),
                CacheEntry("aberto", 40, 2),
            ),
            limitBytes = 100,
            incomingBytes = 50,
            keepId = "aberto",
        )
        assertEquals(listOf("antigo"), removals)
    }
}
