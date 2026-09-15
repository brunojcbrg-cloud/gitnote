package io.github.wiiznokes.gitnote.ui.screen.app.aulas

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.aulas.DriveAuthorization
import io.github.wiiznokes.gitnote.aulas.LessonDraft
import io.github.wiiznokes.gitnote.aulas.LessonFile
import io.github.wiiznokes.gitnote.aulas.LessonHistoryWorker
import io.github.wiiznokes.gitnote.aulas.LessonUploadJob
import io.github.wiiznokes.gitnote.aulas.LessonUploadWorker
import io.github.wiiznokes.gitnote.aulas.readLessonFile
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendLessonScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authorization = remember { DriveAuthorization(context) }
    val draft = remember { LessonDraft() }
    var files by remember { mutableStateOf(emptyList<LessonFile>()) }
    var suggestedName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Selecione todas as partes da mesma aula.") }
    var pendingJob by remember { mutableStateOf<LessonUploadJob?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun enqueue(job: LessonUploadJob) {
        LessonUploadWorker.enqueue(context, job)
        LessonHistoryWorker.schedule(context)
        requestNotificationPermission()
        pendingJob = null
        draft.clear()
        files = emptyList()
        suggestedName = ""
        message = "Aula colocada na fila. Voce ja pode montar a proxima enquanto ela sobe."
    }

    val resolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            authorization.finishResolution(result.data).onSuccess { pendingJob?.let(::enqueue) }
                .onFailure {
                    pendingJob = null
                    message = it.message ?: "Autorizacao do Drive falhou."
                }
        } else {
            pendingJob = null
            message = "Autorizacao do Google Drive cancelada."
        }
    }

    fun authorizeAndEnqueue(job: LessonUploadJob) {
        pendingJob = job
        message = "Conectando ao Google Drive..."
        authorization.request { result ->
            result.onSuccess { access ->
                when {
                    access.accessToken != null -> enqueue(job)
                    access.resolution != null -> resolution.launch(IntentSenderRequest.Builder(access.resolution.intentSender).build())
                    else -> {
                        pendingJob = null
                        message = "O Google Drive nao devolveu autorizacao."
                    }
                }
            }.onFailure {
                pendingJob = null
                message = it.message ?: "Nao foi possivel conectar ao Google Drive."
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        scope.launch {
            val selected = uris.mapNotNull { uri -> runCatching { readLessonFile(context, uri) }.getOrNull() }
            files = draft.add(selected)
            message = if (files.size == 1) "1 arquivo selecionado." else "${files.size} arquivos selecionados."
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeContent,
        topBar = {
            TopAppBar(
                title = { Text("Enviar aula") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message)
            if (files.isEmpty()) {
                Button(onClick = { picker.launch(arrayOf("audio/*", "video/*")) }) { Text("Selecionar arquivos") }
            } else {
                Text("Selecionou todos os arquivos desta aula?")
                OutlinedTextField(
                    value = suggestedName,
                    onValueChange = { suggestedName = it },
                    label = { Text("Nome curto opcional") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(files, key = { it.uri }) { file ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(file.nome)
                                Text("${file.bytes} bytes · ${file.gravadoEm ?: "sem data de gravacao"}")
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker.launch(arrayOf("audio/*", "video/*")) }) { Text("Adicionar mais") }
                    Button(enabled = pendingJob == null, onClick = {
                        val now = ZonedDateTime.now()
                        val short = safeShortName(suggestedName.ifBlank { files.first().nome.substringBeforeLast('.') })
                        authorizeAndEnqueue(
                            LessonUploadJob(
                                idAula = UUID.randomUUID().toString(),
                                criadoEm = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                pastaDrive = "${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))}_$short",
                                nomeSugerido = suggestedName.trim(),
                                arquivos = files,
                            )
                        )
                    }) { Text(if (pendingJob == null) "Confirmar envio" else "Conectando...") }
                }
            }
        }
    }
}

internal fun safeShortName(value: String): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "")
    return normalized.replace(Regex("[^A-Za-z0-9 _-]"), " ").replace(Regex("\\s+"), " ").trim().take(48).ifBlank { "aula" }
}
