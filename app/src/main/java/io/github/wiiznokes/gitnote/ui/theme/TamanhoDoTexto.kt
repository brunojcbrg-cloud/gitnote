package io.github.wiiznokes.gitnote.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

/**
 * Escala da tipografia das notas (pedido 1 de 21/09/2026).
 *
 * Tudo aqui e funcao pura sobre [Typography]/[TextStyle], sem composicao e sem
 * Android, porque e nela que a armadilha do rolador rapido e testada: o
 * `FastScrollLineOverlay` estima a altura da linha como `fontSize * 1.5`, e se a
 * fonte crescer sem a altura de linha crescer junto o dedo vai para uma linha e a
 * tela vai para outra.
 *
 * Por isso [escalar] multiplica `fontSize`, `lineHeight` e `letterSpacing` pelo
 * **mesmo** fator: a razao entre altura de linha e tamanho da fonte fica igual em
 * todos os degraus, que e exatamente o que o rolador supoe.
 */
fun escalar(estilo: TextStyle, fator: Float): TextStyle = estilo.copy(
    fontSize = escalar(estilo.fontSize, fator),
    lineHeight = escalar(estilo.lineHeight, fator),
    letterSpacing = escalar(estilo.letterSpacing, fator),
)

/** Unidade nao declarada continua nao declarada: multiplicar `Unspecified` explode. */
fun escalar(unidade: TextUnit, fator: Float): TextUnit =
    if (unidade.isSpecified) unidade * fator else unidade

/**
 * A tipografia inteira, e nao so o corpo do texto.
 *
 * O pedido fala de "letras das notas **e dos titulos**", e o `scale` que ja
 * existia em `markdownTypographyThemed` nunca tocou os titulos: os seis niveis
 * saiam de `MaterialTheme.typography` sem escala nenhuma. Escalando a tipografia
 * na origem, os dois caminhos de exibicao (o renderizador do modo leitura e o
 * `TextField` do editor) crescem juntos, sem cada um ter a sua propria conta.
 */
fun tipografiaEscalada(base: Typography, fator: Float): Typography {
    if (fator == 1f) return base
    return base.copy(
        displayLarge = escalar(base.displayLarge, fator),
        displayMedium = escalar(base.displayMedium, fator),
        displaySmall = escalar(base.displaySmall, fator),
        headlineLarge = escalar(base.headlineLarge, fator),
        headlineMedium = escalar(base.headlineMedium, fator),
        headlineSmall = escalar(base.headlineSmall, fator),
        titleLarge = escalar(base.titleLarge, fator),
        titleMedium = escalar(base.titleMedium, fator),
        titleSmall = escalar(base.titleSmall, fator),
        bodyLarge = escalar(base.bodyLarge, fator),
        bodyMedium = escalar(base.bodyMedium, fator),
        bodySmall = escalar(base.bodySmall, fator),
        labelLarge = escalar(base.labelLarge, fator),
        labelMedium = escalar(base.labelMedium, fator),
        labelSmall = escalar(base.labelSmall, fator),
    )
}
