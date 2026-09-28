package io.github.wiiznokes.gitnote.ui.screen.app.materials

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.aulas.DRIVE_READONLY_SCOPE
import io.github.wiiznokes.gitnote.aulas.DriveAuthorization
import io.github.wiiznokes.gitnote.aulas.DriveRestClient
import io.github.wiiznokes.gitnote.aulas.MATERIALS_MANIFEST_PATH
import io.github.wiiznokes.gitnote.aulas.MaterialCache
import io.github.wiiznokes.gitnote.aulas.MaterialEntry
import io.github.wiiznokes.gitnote.aulas.MaterialsManifest
import io.github.wiiznokes.gitnote.aulas.parseMaterialsManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsScreen(onBack: () -> Unit, initialMaterialId: String? = null) {
    val context = LocalContext.current
    val authorization = remember { DriveAuthorization(context, listOf(DRIVE_READONLY_SCOPE)) }
    val cache = remember { MaterialCache(context) }
    var token by remember { mutableStateOf<String?>(null) }
    var manifest by remember { mutableStateOf<MaterialsManifest?>(null) }
    var currentFolder by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<MaterialEntry?>(null) }
    var html by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("Lendo o índice sincronizado…") }
    var pending by remember { mutableStateOf<MaterialEntry?>(null) }
    var settings by remember { mutableStateOf(cache.settings()) }

    val resolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            authorization.finishResolution(result.data).onSuccess { token = it }
                .onFailure { message = it.message ?: "Autorização falhou." }
        } else message = "Autorização de leitura cancelada."
    }

    fun connect() {
        authorization.request { result ->
            result.onSuccess { access ->
                when {
                    access.accessToken != null -> token = access.accessToken
                    access.resolution != null -> resolution.launch(IntentSenderRequest.Builder(access.resolution.intentSender).build())
                    else -> message = "O Google não devolveu autorização de leitura."
                }
            }.onFailure { message = it.message ?: "Não foi possível conectar ao Drive." }
        }
    }

    fun openMaterial(material: MaterialEntry) {
        selected = material
        cache.read(material.id)?.let {
            html = it
            message = "Disponível offline"
            return
        }
        pending = material
        if (token == null) connect() else message = "Baixando apostila do Drive…"
    }

    LaunchedEffect(Unit) {
        val root = MyApp.appModule.appPreferences.repoPathSafely()
        val loaded = runCatching {
            withContext(Dispatchers.IO) {
                parseMaterialsManifest(File(root, MATERIALS_MANIFEST_PATH).readText(Charsets.UTF_8))
            }
        }.onFailure { message = it.message ?: "Manifesto de materiais indisponível." }.getOrNull()
        manifest = loaded
        val initial = loaded?.arquivos?.firstOrNull { it.id == initialMaterialId && it.isHtml }
        if (initial != null) openMaterial(initial)
        else if (token == null) connect()
    }

    LaunchedEffect(token, pending) {
        val activeToken = token ?: return@LaunchedEffect
        val material = pending ?: return@LaunchedEffect
        message = "Baixando apostila do Drive…"
        runCatching {
            withContext(Dispatchers.IO) {
                val bytes = DriveRestClient(context, activeToken).downloadFile(material.id)
                cache.store(material.id, bytes)
                bytes.toString(Charsets.UTF_8)
            }
        }.onSuccess {
            html = it
            pending = null
            message = "Guardada para abrir offline"
        }.onFailure { message = it.message ?: "Não foi possível baixar o material." }
    }

    val activeHtml = html
    val activeMaterial = selected
    if (activeHtml != null && activeMaterial != null) {
        MaterialWebView(activeMaterial.name, activeHtml) {
            html = null
            selected = null
        }
        return
    }

    val all = manifest?.arquivos.orEmpty()
    val folders = all.mapNotNull { entry ->
        val relative = entry.caminho.removePrefix(if (currentFolder.isBlank()) "" else "$currentFolder/")
        val first = relative.substringBefore('/')
        if ('/' in relative) first else null
    }.distinct().sorted()
    val files = all.filter { entry ->
        val parent = entry.caminho.substringBeforeLast('/', "")
        parent == currentFolder
    }.sortedBy { it.name.lowercase() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeContent,
        topBar = {
            TopAppBar(
                title = { Text(if (currentFolder.isBlank()) "Materiais" else currentFolder.substringAfterLast('/')) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentFolder.isBlank()) onBack() else currentFolder = currentFolder.substringBeforeLast('/', "")
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Cache offline", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(100, 250, 500).forEach { limit ->
                                FilterChip(
                                    selected = settings.first == limit,
                                    onClick = { settings = limit to settings.second; cache.updateSettings(limit, settings.second) },
                                    label = { Text("$limit MB") },
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Baixar também em rede móvel")
                            Switch(
                                checked = settings.second,
                                onCheckedChange = { enabled ->
                                    settings = settings.first to enabled
                                    cache.updateSettings(settings.first, enabled)
                                },
                            )
                        }
                    }
                }
            }
            items(folders, key = { "folder:$it" }) { folder ->
                Card(Modifier.fillMaxWidth().clickable {
                    currentFolder = listOf(currentFolder, folder).filter(String::isNotBlank).joinToString("/")
                }) { Text("📁  $folder", Modifier.padding(16.dp)) }
            }
            items(files, key = { it.id }) { material ->
                Card(
                    Modifier.fillMaxWidth().clickable(enabled = material.isHtml) {
                        openMaterial(material)
                    },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(material.name)
                        Text(
                            if (material.isHtml) "HTML · ${(material.size / 1_000).coerceAtLeast(1)} KB" else "PDF · abra pela web",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (manifest == null) item { Button(onClick = { connect() }) { Text("Autorizar leitura do Drive") } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialWebView(title: String, html: String, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        contentWindowInsets = WindowInsets.safeContent,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            )
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = {
                WebView(it).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val uri = request?.url ?: return true
                            if (uri.scheme == "http" || uri.scheme == "https") {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString())))
                            }
                            return true
                        }
                    }
                    val encoded = Base64.getEncoder().encodeToString(html.toByteArray(Charsets.UTF_8))
                    loadData(encoded, "text/html", "base64")
                }
            },
        )
    }
}
