package io.github.wiiznokes.gitnote.ui.screen.app.aulas

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.aulas.DriveAuthorization
import io.github.wiiznokes.gitnote.aulas.DriveHttpException
import io.github.wiiznokes.gitnote.aulas.DriveRestClient
import io.github.wiiznokes.gitnote.aulas.LessonHistoryWorker
import io.github.wiiznokes.gitnote.aulas.MobileLessonState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val authorization = remember { DriveAuthorization(context) }
    var token by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf(MobileLessonState()) }
    var selectedMatter by remember { mutableStateOf<String?>(null) }
    // null = "Todas": a materia mostra as aulas da raiz E das subpastas juntas.
    var selectedUnit by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("Conectando ao Google Drive...") }

    val resolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            authorization.finishResolution(result.data).onSuccess { token = it }
                .onFailure { message = it.message ?: "Autorizacao falhou." }
        } else message = "Autorizacao cancelada."
    }

    fun connect() {
        authorization.request { result ->
            result.onSuccess { access ->
                when {
                    access.accessToken != null -> token = access.accessToken
                    access.resolution != null -> resolution.launch(IntentSenderRequest.Builder(access.resolution.intentSender).build())
                    else -> message = "O Google Drive nao devolveu autorizacao."
                }
            }.onFailure { message = it.message ?: "Nao foi possivel conectar." }
        }
    }

    // Conecta sozinho ao abrir a tela. O token vive em `remember`, entao some a cada saida —
    // sem isto o Bruno tinha de tocar em "Conectar Google Drive" toda vez. Nao ha tela extra
    // no caminho feliz: com o acesso ja concedido, authorize() devolve o token sem UI nenhuma,
    // e a resolucao so aparece na primeira vez ou se o acesso for revogado.
    LaunchedEffect(Unit) {
        if (token == null) connect()
    }

    LaunchedEffect(token) {
        val activeToken = token ?: return@LaunchedEffect
        LessonHistoryWorker.schedule(context)
        while (true) {
            runCatching { withContext(Dispatchers.IO) { DriveRestClient(context, activeToken).downloadState() } }
                .onSuccess { loaded ->
                    state = loaded
                    val loadedMatters = loaded.aulas
                        .map { it.materia.ifBlank { "Sem materia" } }
                        .distinct()
                        .sorted()
                    if (selectedMatter !in loadedMatters) selectedMatter = loadedMatters.firstOrNull()
                    message = freshnessMessage(loaded.pcPublicadoEm)
                }
                .onFailure {
                    if (it is DriveHttpException && it.status == 401) token = null
                    message = if (it is DriveHttpException && it.status == 401) {
                        "A autorizacao expirou. Conecte o Google Drive novamente."
                    } else {
                        it.message ?: "Falha ao atualizar o historico."
                    }
                }
            delay(60_000)
        }
    }

    val matters = state.aulas.map { it.materia.ifBlank { "Sem materia" } }.distinct().sorted()
    val ofMatter = state.aulas.filter { (it.materia.ifBlank { "Sem materia" }) == selectedMatter }
    // Subpastas existentes dentro da materia escolhida (P1, P2...), se houver.
    val units = ofMatter.map { it.unidade }.filter { it.isNotBlank() }.distinct().sorted()
    val visible = if (selectedUnit == null) ofMatter else ofMatter.filter { it.unidade == selectedUnit }
    Scaffold(
        contentWindowInsets = WindowInsets.safeContent,
        topBar = {
            TopAppBar(
                title = { Text("Historico de aulas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(message)
            if (token == null) Button(onClick = ::connect) { Text("Conectar Google Drive") }
            if (state.atualizadoEm.isNotBlank()) Text("Atualizado as ${state.atualizadoEm.substringAfter('T').take(5)}")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(matters, key = { it }) { matter ->
                    FilterChip(
                        selected = selectedMatter == matter,
                        onClick = { selectedMatter = matter; selectedUnit = null },
                        label = { Text(matter) },
                    )
                }
            }
            if (units.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedUnit == null,
                            onClick = { selectedUnit = null },
                            label = { Text("Todas (${ofMatter.size})") },
                        )
                    }
                    items(units, key = { it }) { unit ->
                        val quantas = ofMatter.count { it.unidade == unit }
                        FilterChip(
                            selected = selectedUnit == unit,
                            onClick = { selectedUnit = unit },
                            label = { Text("$unit ($quantas)") },
                        )
                    }
                }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (token != null && visible.isEmpty()) {
                    item { Text("Nenhuma aula processada nesta materia.") }
                }
                items(visible, key = { it.idAula }) { lesson ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(lesson.nomeFinal.ifBlank { "Aula" })
                            Text(
                                "Materia: " + lesson.materia.ifBlank { "sem materia" } +
                                    if (lesson.unidade.isNotBlank()) "  ›  ${lesson.unidade}" else ""
                            )
                            Text("Processada: ${lesson.processadoEm.ifBlank { "sem data" }}")
                            Text("Gravada: ${lesson.aulaGravadaEm?.let { "${it.data.orEmpty()} ${it.hora.orEmpty()}" } ?: "sem data"}")
                            Text("Originais: ${lesson.arquivosOriginais.joinToString().ifBlank { "nao registrados" }}")
                            if (lesson.pendenteTriagem) Text("Aguardando classificacao na CENTRAL")
                        }
                    }
                }
            }
        }
    }
}

internal fun freshnessMessage(pcPublishedAt: String, now: ZonedDateTime = ZonedDateTime.now()): String {
    val published = runCatching { ZonedDateTime.parse(pcPublishedAt) }.getOrNull()
        ?: return "O PC ainda nao publicou estado."
    val minutes = Duration.between(published, now).toMinutes().coerceAtLeast(0)
    return if (minutes > 20) "PC sem publicar estado ha $minutes min; a sincronizacao pode estar parada."
    else "Estado do PC publicado ha $minutes min."
}
