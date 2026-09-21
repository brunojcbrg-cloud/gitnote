package io.github.wiiznokes.gitnote.ui.screen.app.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wiiznokes.gitnote.ui.component.markdown.MarkdownLivePreviewTransformation
import io.github.wiiznokes.gitnote.ui.viewmodel.edit.MarkDownVM
import kotlin.math.roundToInt

private class PosicaoPopupDoCursor(
    private val cursor: IntRect,
    private val folga: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = cursor.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val abaixo = cursor.bottom + folga
        val acima = cursor.top - folga - popupContentSize.height
        val y = if (abaixo + popupContentSize.height <= windowSize.height) abaixo else acima.coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
internal fun WikilinkEditorField(
    vm: MarkDownVM,
    textFocusRequester: FocusRequester,
    onFinished: () -> Unit,
    textContent: TextFieldValue,
    visualTransformation: VisualTransformation,
    textStyle: TextStyle,
) {
    val estado by vm.sugestaoWikilink.collectAsStateWithLifecycle()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var coordenadas by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var retanguloDoCursor by remember { mutableStateOf<Rect?>(null) }
    val interactionSource = remember { MutableInteractionSource() }

    val fecharAoArrastar = Modifier.pointerInput(estado.visivel) {
        if (!estado.visivel) return@pointerInput
        awaitPointerEventScope {
            while (true) {
                val evento = awaitPointerEvent(PointerEventPass.Initial)
                if (evento.changes.any { it.positionChanged() }) {
                    vm.fecharSugestaoWikilink()
                    break
                }
            }
        }
    }

    BasicTextField(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .focusRequester(textFocusRequester)
            .onGloballyPositioned { coordenadas = it }
            .then(fecharAoArrastar)
            .onPreviewKeyEvent { evento ->
                if (!estado.visivel || evento.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (evento.key) {
                    Key.DirectionDown -> vm.moverSelecaoWikilink(1)
                    Key.DirectionUp -> vm.moverSelecaoWikilink(-1)
                    Key.Enter, Key.NumPadEnter -> vm.aceitarSugestaoWikilink()
                    Key.Escape -> vm.fecharSugestaoWikilink()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
        value = textContent,
        onValueChange = vm::onValueChange,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onBackground),
        keyboardActions = KeyboardActions(onDone = { vm.save(onSuccess = onFinished) }),
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        onTextLayout = { resultado ->
            layout = resultado
            val original = textContent.selection.end.coerceIn(0, textContent.text.length)
            val transformado = (visualTransformation as? MarkdownLivePreviewTransformation)
                ?.originalParaTransformado(original)
                ?: original
            retanguloDoCursor = resultado.getCursorRect(
                transformado.coerceIn(0, resultado.layoutInput.text.length),
            )
        },
    )

    val resultado = layout
    val coords = coordenadas
    val cursorLocal = retanguloDoCursor
    if (estado.visivel && resultado != null && coords != null && cursorLocal != null) {
        val topo = coords.localToWindow(Offset(cursorLocal.left, cursorLocal.top))
        val base = coords.localToWindow(Offset(cursorLocal.right, cursorLocal.bottom))
        val cursorNaJanela = IntRect(
            left = topo.x.roundToInt(),
            top = topo.y.roundToInt(),
            right = base.x.roundToInt(),
            bottom = base.y.roundToInt(),
        )
        val folga = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.roundToPx() }
        Popup(
            popupPositionProvider = remember(cursorNaJanela, folga) {
                PosicaoPopupDoCursor(cursorNaJanela, folga)
            },
            onDismissRequest = vm::fecharSugestaoWikilink,
            properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
        ) {
            Surface(
                modifier = Modifier.widthIn(min = 220.dp, max = 360.dp).heightIn(max = 280.dp),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
            ) {
                LazyColumn {
                    if (estado.carregando) {
                        item {
                            Text(
                                text = "Carregando seções…",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        }
                    }
                    estado.mensagem?.let { mensagem ->
                        item {
                            Text(
                                text = mensagem,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    itemsIndexed(
                        items = estado.itens,
                        key = { indice, item -> "$indice|${item.caminho}|${item.texto}" },
                    ) { indice, item ->
                        val fundo = if (indice == estado.selecionado) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(fundo)
                                .clickable { vm.aceitarSugestaoWikilink(indice) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.texto, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = item.detalhe,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
