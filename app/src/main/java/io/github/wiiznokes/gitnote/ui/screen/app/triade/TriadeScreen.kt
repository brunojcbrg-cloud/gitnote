package io.github.wiiznokes.gitnote.ui.screen.app.triade

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.R
import io.github.wiiznokes.gitnote.triade.CAMINHO_TRIADE_NO_VAULT
import io.github.wiiznokes.gitnote.triade.ClienteTriade
import io.github.wiiznokes.gitnote.triade.EstadoRegistroTriade
import io.github.wiiznokes.gitnote.triade.EstadoTriade
import io.github.wiiznokes.gitnote.triade.FuncaoTriade
import io.github.wiiznokes.gitnote.triade.filtrarFuncoesTriade
import io.github.wiiznokes.gitnote.triade.lerRegistroTriade
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun rotuloCliente(cliente: ClienteTriade): String = when (cliente) {
    ClienteTriade.pc -> "PC"
    ClienteTriade.celular -> "Celular"
    ClienteTriade.web -> "Web"
}

private fun rotuloEstado(estado: EstadoTriade): String = when (estado) {
    EstadoTriade.feito -> "Feito"
    EstadoTriade.parcial -> "Parcial"
    EstadoTriade.pendente -> "Pendente"
    EstadoTriade.nao_se_aplica -> "Não se aplica"
    EstadoTriade.nao_verificado -> "Não verificado"
}

private fun corDoEstado(estado: EstadoTriade): Color = when (estado) {
    EstadoTriade.feito -> Color(0xFF2E7D32)
    EstadoTriade.parcial -> Color(0xFFF9A825)
    EstadoTriade.pendente -> Color(0xFFC62828)
    EstadoTriade.nao_se_aplica -> Color(0xFF757575)
    EstadoTriade.nao_verificado -> Color(0xFF616161)
}

/**
 * Só leitura: o mesmo registro (`05_Sistema/triade/funcoes.json`) que o Life
 * SO do PC e o notas-web mostram, lido aqui do clone do vault. Mesmos
 * filtros dos outros dois clientes (§4.4 do handoff da tríade).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriadeScreen(onBack: () -> Unit) {
    var estado by remember { mutableStateOf<EstadoRegistroTriade?>(null) }
    LaunchedEffect(Unit) {
        estado = withContext(Dispatchers.IO) {
            val raiz = MyApp.appModule.appPreferences.repoPathSafely()
            lerRegistroTriade(File(raiz, CAMINHO_TRIADE_NO_VAULT))
        }
    }

    var clienteFiltro by remember { mutableStateOf<ClienteTriade?>(null) }
    var soPendentes by remember { mutableStateOf(false) }
    var expandidas by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeContent,
        topBar = {
            TopAppBar(
                title = { Text("Tríade") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back))
                    }
                },
            )
        },
    ) { padding ->
        when (val estadoAtual = estado) {
            null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            is EstadoRegistroTriade.Ausente,
            is EstadoRegistroTriade.Invalido,
            is EstadoRegistroTriade.Indisponivel -> {
                val mensagem = when (estadoAtual) {
                    is EstadoRegistroTriade.Ausente -> estadoAtual.mensagem
                    is EstadoRegistroTriade.Invalido -> estadoAtual.mensagem
                    is EstadoRegistroTriade.Indisponivel -> estadoAtual.mensagem
                    is EstadoRegistroTriade.Pronto -> ""
                }
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    Text(mensagem, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is EstadoRegistroTriade.Pronto -> {
                val funcoes = filtrarFuncoesTriade(estadoAtual.registro.funcoes, clienteFiltro, soPendentes)
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = clienteFiltro == null,
                                onClick = { clienteFiltro = null },
                                label = { Text("Todos") },
                            )
                        }
                        items(ClienteTriade.entries, key = { it.name }) { cliente ->
                            FilterChip(
                                selected = clienteFiltro == cliente,
                                onClick = { clienteFiltro = cliente },
                                label = { Text("Falta no ${rotuloCliente(cliente)}") },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = soPendentes, onCheckedChange = { soPendentes = it })
                        Text("Só pendentes")
                    }
                    Text(
                        text = "${funcoes.size} funç${if (funcoes.size == 1) "ão" else "ões"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(funcoes, key = { it.id }) { funcao ->
                            FuncaoTriadeCard(
                                funcao = funcao,
                                expandida = funcao.id in expandidas,
                                aoAlternar = {
                                    expandidas = if (funcao.id in expandidas) {
                                        expandidas - funcao.id
                                    } else {
                                        expandidas + funcao.id
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FuncaoTriadeCard(
    funcao: FuncaoTriade,
    expandida: Boolean,
    aoAlternar: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = aoAlternar),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(funcao.titulo, style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                for (cliente in ClienteTriade.entries) {
                    val clienteFuncao = funcao.clientes.getValue(cliente)
                    Text(
                        text = "${rotuloCliente(cliente)}: ${rotuloEstado(clienteFuncao.estado)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = corDoEstado(clienteFuncao.estado),
                    )
                }
            }
            if (expandida) {
                Text(
                    text = funcao.descricao,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = funcao.origem,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                for (cliente in ClienteTriade.entries) {
                    val clienteFuncao = funcao.clientes.getValue(cliente)
                    val detalhe = listOfNotNull(
                        clienteFuncao.onde.takeIf { it.isNotBlank() },
                        clienteFuncao.versao.takeIf { it.isNotBlank() },
                        clienteFuncao.nota.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    if (detalhe.isNotBlank()) {
                        Text(
                            text = "${rotuloCliente(cliente)}: $detalhe",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
