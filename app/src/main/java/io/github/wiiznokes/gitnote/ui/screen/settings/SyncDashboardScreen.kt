package io.github.wiiznokes.gitnote.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wiiznokes.gitnote.data.StartupSyncState
import io.github.wiiznokes.gitnote.manager.GitSyncSummary
import io.github.wiiznokes.gitnote.ui.component.AppPage
import io.github.wiiznokes.gitnote.ui.component.SimpleIcon
import io.github.wiiznokes.gitnote.ui.viewmodel.SyncDashboardViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun SyncDashboardScreen(
    onBackClick: () -> Unit,
    startupState: StartupSyncState,
    syncRevision: Long,
    onSyncNow: () -> Unit,
) {
    val vm: SyncDashboardViewModel = viewModel()
    val state by vm.state.collectAsState()
    val lastAt by vm.prefs.lastSyncEpochMillis.getAsState()
    val lastResult by vm.prefs.lastSyncResult.getAsState()
    LaunchedEffect(syncRevision) { if (syncRevision > 0) vm.refresh() }

    AppPage(title = "Sincronização", onBackClick = onBackClick) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(
                        text = summaryTitle(state.summary),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = summarySubtitle(state.summary),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (state.summary is GitSyncSummary.Loading) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            } else state.snapshot?.let { snapshot ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Repositório", fontWeight = FontWeight.SemiBold)
                        Text("${snapshot.ahead} à frente · ${snapshot.behind} atrás")
                        Text("${snapshot.changes.count { it.path.endsWith(".md", true) }} notas ainda não enviadas")
                        snapshot.changes.take(5).forEach { change ->
                            Text("${change.kindLabel()} · ${change.path}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Mudanças recebidas recentemente", fontWeight = FontWeight.SemiBold)
                        if (snapshot.recentCommits.isEmpty()) Text("Nenhuma mudança disponível")
                        snapshot.recentCommits.forEach { commit ->
                            Text(
                                "${commit.shortHash} · ${commit.message}",
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                "${commit.author} · ${commit.files.take(3).joinToString()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Última sincronização", fontWeight = FontWeight.SemiBold)
                    Text(formatLastSync(lastAt))
                    Text(lastResult, style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(
                onClick = onSyncNow,
                enabled = startupState !is StartupSyncState.Syncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SimpleIcon(imageVector = Icons.Default.Refresh)
                Text(
                    if (startupState is StartupSyncState.Syncing) "Sincronizando…" else "Sincronizar agora",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

private fun summaryTitle(summary: GitSyncSummary) = when (summary) {
    GitSyncSummary.Loading -> "Conferindo o repositório"
    GitSyncSummary.Updated -> "Tudo atualizado"
    is GitSyncSummary.Behind -> "${summary.commits} mudanças para receber"
    is GitSyncSummary.LocalEdits -> "${summary.notes} notas para enviar"
    is GitSyncSummary.Diverged -> "Mudanças dos dois lados"
    is GitSyncSummary.Failed -> "Não foi possível conferir"
}

private fun summarySubtitle(summary: GitSyncSummary) = when (summary) {
    GitSyncSummary.Loading -> "Atualizando a referência remota, sem alterar suas notas."
    GitSyncSummary.Updated -> "O celular e o GitHub apontam para a mesma versão."
    is GitSyncSummary.Behind -> "Abra a sincronização para receber com segurança."
    is GitSyncSummary.LocalEdits -> "Há edições locais que ainda não chegaram ao GitHub."
    is GitSyncSummary.Diverged -> "${summary.ahead} à frente, ${summary.behind} atrás e ${summary.notes} notas locais."
    is GitSyncSummary.Failed -> summary.message
}

private fun io.github.wiiznokes.gitnote.manager.GitWorkingTreeChange.kindLabel() = when (kind) {
    "new" -> "nova"
    "deleted" -> "apagada"
    else -> "editada"
}

private fun formatLastSync(raw: String): String {
    val millis = raw.toLongOrNull()?.takeIf { it > 0 } ?: return "Nunca"
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
}
