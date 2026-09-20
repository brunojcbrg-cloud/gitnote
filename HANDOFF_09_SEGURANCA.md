# HANDOFF 09 — segurança: cofre de credenciais, trava de abertura e portão no CI

**Data:** 2026-09-20
**Destinatário:** Codex
**Repositório:** `E:\Projetos\gitnote` (fork `brunojcbrg-cloud/gitnote`)
**Commit base:** `2c2b18f` (`master`)
**Branch aberta:** `posicao-de-leitura` (`60f093f`, 2 commits à frente). Ela mexe em
`PosicoesDeLeitura.kt` e `MarkDownVM.kt` — **nenhum arquivo deste handoff**. Não faça merge dela aqui.
**Tamanho:** grande, mas fatiado em cinco. As fatias 1, 2 e 5 são independentes e baratas. A 3 é o
núcleo. A **fatia 4 é decisão do Bruno e não deve ser executada sem o "sim" explícito dele.**

---

## 0. Como ler este handoff

Cada fatia tem **fatos medidos** (já apurados em 20/09/2026, não reapurar), **o que fazer** e
**testes obrigatórios**. Onde estiver escrito "meça antes", meça de verdade e **reporte o que
encontrou**, mesmo que a conclusão seja "o handoff estava errado". Hipótese derrubada por medição é
entrega, não fracasso.

Não há SDK Android nem JDK na máquina do Bruno. **Não tente compilar localmente.** O
`fork-release.yml` dispara sozinho em push na `master` e roda `testDebugUnitTest` antes de montar o
APK; o log lista cada teste executado. Use isso como prova.

Leia antes de começar: `RESULTADO_08_TABELAS.md` (números do editor e armadilhas já pagas).

**Regra que atravessa o handoff inteiro:** toda lógica de criptografia entra como **núcleo puro atrás
de uma interface**, testável na JVM sem Keystore e sem emulador. O CI não tem emulador — só
`testDebugUnitTest`. Código que só dá para testar no aparelho é código sem prova.

---

## 1. O pedido, nas palavras dele

> "Precisamos trabalhar a segurança do meu app de celular. Ele não tem autenticação nenhuma, de forma
> que qualquer pessoa com o link da publicação dele consegue baixar meu app e acessar minhas coisas.
> O que eu preciso é colocar uma autenticação nele e que seja realmente seguro, e que todos os testes
> no app como um todo sejam feitos de forma que identifiquem falhas na segurança e que dificulte uma
> pessoa quebrar ele."

---

## 2. Diagnóstico medido em 20/09/2026 — não reapurar

### 2.1 A premissa dele está meio errada, e isso muda a prioridade

O APK publicado (`gitnote-fork-49.apk`, release **pública**, 24,9 MB) foi baixado e varrido entrada
por entrada. **Não existe dado do Bruno dentro dele:**

| Procurado no APK | Resultado |
|---|---|
| `brunojcbrg` (usuário/e-mail) | não existe |
| `vault-conhecimento` (nome do repo) | não existe |
| PAT do GitHub (`ghp_`, `github_pat_`) | não existe |
| Chave SSH privada | não existe (o `BEGIN PRIVATE KEY` em `libgit_wrapper.so` é string do parser do libgit2) |
| Chave de API Google | não existe |
| `clientSecret` do OAuth | **existe**, em `classes.dex` |

O repo `brunojcbrg-cloud/vault-conhecimento` é **privado** (conferido). Quem baixa o APK recebe um app
vazio e teria de configurar a conta dele. **Não implemente nada partindo de "o APK vaza os dados".**

### 2.2 A ameaça real é o inverso: escrever no app dele

```
SHA-1 do certificado que assinou o APK publicado (extraído do APK Signing Block v2):
73:96:32:A7:78:E2:09:63:FE:75:DD:EB:96:48:D8:14:FB:16:E5:86
```

É exatamente `app/nightly-signing-key.jks`, **commitado no repo público**, senha `123456` em texto puro
em `app/build.gradle.kts:88-93`. Qualquer pessoa compila um APK com o código que quiser, assina com
essa chave, e o Android aceita como **atualização do app instalado no celular dele** — herdando todo o
`/data/data`: chave SSH, token, notas. Não é "baixar o app dele"; é poder **substituir** o app dele.

Isso veio do upstream e lá faz sentido. Num fork público com o vault médico dentro, não faz.
É a fatia 4, e é decisão do Bruno porque tem custo (§7).

### 2.3 Achados no código (arquivo:linha)

1. **Nenhuma trava e nenhuma criptografia.** Busca por
   `biometric|BiometricPrompt|EncryptedSharedPreferences|MasterKey|Keystore|KeyGenParameterSpec|androidx.security|FLAG_SECURE`
   em todo o projeto: **zero ocorrências**. O app abre direto nas notas.

2. **Credenciais em texto puro.** `data/AppPreferences.kt:89-94` — `userPassPassword`, `privateKey`,
   `passphrase`, `appAuthToken` são `stringPreference` crus. Ficam em
   `/data/data/io.github.wiiznokes.gitnote.nightly/files/datastore/settings.preferences_pb`.

3. **`android:allowBackup="true"` com as regras em branco.** `AndroidManifest.xml:21`;
   `res/xml/backup_rules.xml` e `res/xml/data_extraction_rules.xml` estão como vieram do template,
   sem um único `<exclude>`. A chave SSH privada entra no Auto Backup e na transferência D2D.

4. **O token OAuth de escopo `repo` fica guardado para sempre e não serve para nada.**
   Medido: `appAuthToken` é escrito em `SetupViewModel.kt:288` e lido **apenas** em
   `SetupViewModel.kt:333, 369, 383` (criar repo, adicionar deploy key) e nas telas de setup
   (`AuthorizeGitNoteScreen.kt`, `RemoteNav.kt:88`). **Nenhum uso fora do setup.**
   A credencial de regime permanente é a `Cred.Ssh` — uma **deploy key por repositório**, gerada em
   `SetupViewModel.kt:346` e `:396`, usada por `StorageManager.kt:71, 183, 385`.
   Ou seja: o app carrega leitura+escrita de *todos* os repos privados da conta, sem precisar.

5. **Fluxo OAuth interceptável.** `provider/GitHub.kt:20` (clientId) e `:24` (clientSecret, hardcoded
   e extraível do dex). `getLaunchOAuthScreenUrl()` monta a URL **sem `state` e sem PKCE**. O retorno
   é por *custom scheme* (`AndroidManifest.xml:43`, tratado em `MainActivity.kt:107`). Qualquer app
   instalado registra o mesmo scheme, captura o `code` e troca por token. A RFC 8252 desaconselha
   exatamente esse desenho. **A fatia 1.2 esvazia esse risco sem tocar no fluxo.**

6. **`MANAGE_EXTERNAL_STORAGE`** (`AndroidManifest.xml:10`) — acesso a todo o armazenamento.

7. **Sem `FLAG_SECURE`** — as notas entram no print da tela de recentes e em gravação de tela.

### 2.4 O que já está certo — não "conserte"

- Dependências nativas atuais: libgit2 1.9.4, git2 0.21, openssl-sys 0.9.117.
- Zero `http://` no código; `usesCleartextTraffic` ausente (padrão seguro no targetSdk atual).
- O build `nightly` herda `release`: **tem R8/minify e não é debuggable**. `run-as` não funciona nele.
- Só `MainActivity` é exportada. Nenhum `ContentProvider` exposto.
- O Drive usa Google Identity Services com escopo `drive.file` e HTTPS — desenho correto.
- **Só existe um `Worker` no app** (`aulas/LessonWorkers.kt`) e ele **não toca em `Cred`**.
  `pull`/`push` (`GitManager.kt:176, 191`) só são chamados por `StorageManager`, em primeiro plano.
  **Isso é o que libera exigir autenticação por uso na chave do Keystore (§6): não há sync de git em
  segundo plano para quebrar.** Confirme os três call sites antes de fechar a fatia 3.

---

## 3. A única coisa que falta medir antes de escrever código

**Em qual `StorageConfig` o celular do Bruno está?** (`AppPreferences.kt:154`, enum em `:213`)

- `StorageConfig.App` → o clone vive em `filesDir` (privado do app). Nenhum outro app lê.
- `StorageConfig.Device` → o clone vive em pasta compartilhada. **Qualquer app com permissão de
  armazenamento lê o vault inteiro, sem root e sem trava nenhuma.** Se for esse o caso, isso vira a
  fatia mais urgente de todas e este handoff precisa de um item novo.

Como medir sem adivinhar: acrescente a informação na tela de Ajustes (é útil de forma permanente,
não é instrumentação descartável) ou peça ao Bruno para olhar o caminho do repositório que o app já
mostra. **Reporte o valor antes de começar a fatia 3.** Não redesenhe armazenamento por conta própria.

---

## 4. Fatia 1 — parar o vazamento barato (sem cripto, sem UI nova)

Três mudanças pequenas e independentes. Commit separado para cada uma.

### 4.1 Tirar as credenciais do backup

Em `AndroidManifest.xml`, `android:allowBackup="false"`.

Se preferir manter o backup das preferências de aparência (não é obrigatório, e a opção mais simples
é desligar tudo), então mantenha `true` e escreva os `<exclude>` de verdade nos **dois** arquivos —
`backup_rules.xml` (`<full-backup-content>`) e `data_extraction_rules.xml` (`<cloud-backup>` **e**
`<device-transfer>`, que hoje está comentado) — excluindo o DataStore `settings`.
**Meia exclusão é pior que nenhuma**, porque parece resolvido.

### 4.2 Apagar o token OAuth no fim do setup

Medido na §2.3 item 4: `appAuthToken` não é usado depois do setup. Assim que a deploy key estiver
adicionada e o clone concluído com sucesso, chame `prefs.appAuthToken.reset()`.

Cuidado com a ordem: `AuthorizeGitNoteScreen.kt:74` usa `appAuthToken.isNotEmpty()` para decidir se
já está autorizado. Apagar cedo demais faz o setup pedir OAuth de novo no meio do fluxo. Apague
**depois** do `onSuccess` do clone, não antes.

Se o Bruno já tem o app configurado, o token velho continua gravado — a migração da §5.3 tem de
apagá-lo também.

### 4.3 `FLAG_SECURE` com chave nos Ajustes

`booleanPreference("bloquearCapturaDeTela", true)` em `AppPreferences.kt`, aplicado em `MainActivity`
via `window.setFlags(FLAG_SECURE, FLAG_SECURE)` / `clearFlags`. Reaja à mudança sem exigir reinício.

Aqui **uma opção nos Ajustes é justificada** (ao contrário da regra do handoff 08): ele às vezes vai
querer mandar print de uma nota. Siga o padrão de `isMarkdownThemeActive`
(`AppPreferences.kt:182`) e a estrutura de `ui/screen/settings/SettingsScreen.kt`.

---

## 5. Fatia 2 — cofre: credencial cifrada pelo Android Keystore

### 5.1 A decisão de biblioteca, já tomada — não reabra

A pesquisa aponta `DataStore + Tink + Keystore` porque o `androidx.security:security-crypto` foi
**deprecado em abril de 2025** (1.1.0-alpha07). Mas Tink/`StreamingAead` resolve **arquivo grande**.
Aqui o segredo são **quatro strings curtas**. Então:

**Use `AES/GCM/NoPadding` com chave no `AndroidKeyStore` direto, sem dependência nova.**
Não adicione Tink. Não adicione `androidx.security`. Não escreva criptografia à mão.

### 5.2 O desenho — envelope de dois níveis (isto importa)

Uma chave do Keystore com autenticação por uso só consegue **uma** operação por `CryptoObject`.
Decifrar quatro valores exigiria quatro biometrias. Então:

```
chaveDoKeystore (AndroidKeyStore, exige autenticação)
      └─ cifra/decifra ─> chaveDeDados (AES-256, existe só em memória depois da autenticação)
                                 └─ cifra/decifra ─> privateKey, passphrase,
                                                     userPassPassword, appAuthToken
```

Uma autenticação abre a `chaveDeDados`; ela atende as quatro leituras enquanto a sessão dura. A
`chaveDeDados` cifrada (o "envelope") é guardada no próprio DataStore, em Base64.

**O núcleo puro é o envelope**, e é onde tem que estar o volume de testes:

- `interface ChaveMestra { fun abrir(envelope: ByteArray): ByteArray; fun selar(chave: ByteArray): ByteArray }`
  — a implementação real fala com o Keystore; a de teste é um AES fixo em memória.
- `class CofreDeCredenciais(private val mestra: ChaveMestra)` — cifra/decifra string ⇄ Base64, com IV
  aleatório de 12 bytes **por valor** e tag GCM de 128 bits. IV concatenado ao ciphertext, nunca
  reaproveitado.
- `AppPreferences` passa a expor `privateKey`, `passphrase`, `userPassPassword` e `appAuthToken`
  **através do cofre**, mantendo a mesma assinatura pública (`get()`, `update()`), para que
  `StorageManager` e `SetupViewModel` não mudem.

Parâmetros da chave real do Keystore (minSdk é 30, tudo abaixo é suportado):

```
KeyGenParameterSpec.Builder(alias, PURPOSE_ENCRYPT or PURPOSE_DECRYPT)
    .setBlockModes(BLOCK_MODE_GCM)
    .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .setUserAuthenticationRequired(true)
    .setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL)
```

`0` = exigir autenticação a cada uso. Vale porque **não há sync de git em segundo plano** (§2.4).

### 5.3 Migração — o passo que decide se isso serve para alguma coisa

O celular dele **já tem** a chave SSH em texto puro gravada. Se a migração não rodar, o conserto não
vale nada no aparelho dele.

Na primeira execução após a atualização, **depois da primeira autenticação bem-sucedida**: ler os
valores em claro, gravá-los cifrados sob chaves novas, e **remover as chaves antigas do DataStore**
com `MutablePreferences.remove(key)` — remover mesmo, não gravar string vazia. Aproveite e apague
`appAuthToken` (§4.2). Grave uma marca `credenciaisCifradas = true` para não repetir.

### 5.4 Os dois modos de falha que precisam de tratamento explícito

1. **Aparelho sem bloqueio de tela.** `BiometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)`
   devolve erro. Uma chave com `setUserAuthenticationRequired(true)` **não pode nem ser criada**.
   O app não pode travar nem quebrar: caia para o comportamento de hoje (sem cofre) e **diga na tela
   de Ajustes que a proteção está desligada porque não há bloqueio de tela**. Silêncio aqui é a pior
   saída, porque ele vai achar que está protegido.
2. **`KeyPermanentlyInvalidatedException`.** A chave morre se ele remover o bloqueio de tela ou
   cadastrar biometria nova (depende do fabricante). As credenciais ficam **irrecuperáveis** — e isso
   é o comportamento correto, não um bug. O app tem de capturar a exceção e oferecer "reconfigurar o
   repositório", não estourar. **Sem tratamento, ele perde o app e não entende por quê.**

---

## 6. Fatia 3 — a trava de abertura

### 6.1 Seja honesto no que ela protege — e diga isso na tela

Com o desenho da §5, a autenticação protege **criptograficamente as credenciais**: sem biometria não
existe chave, e sem chave não há sync nem acesso ao GitHub.

As **notas**, não. Elas são arquivos `.md` em claro no disco. A trava sobre elas é de **interface**:
resolve "alguém pegou meu celular destravado", que é a ameaça realista, e **não** resolve "alguém com
root ou com um APK assinado pela chave pública". Proteger as notas criptograficamente exigiria cifrar
a árvore de trabalho, o que quebra o git e o Obsidian no PC.

**Essa é uma decisão do Bruno e está fora deste handoff.** Escreva a limitação em uma frase na tela de
Ajustes, abaixo da chave da trava. Não a esconda e não prometa mais do que entrega.

### 6.2 O que entregar

- Dependência: `androidx.biometric:biometric` **1.1.0** (estável; a 1.2.0 segue em alpha há anos).
  Entre pelo catálogo `gradle/libs.versions.toml`, no padrão do arquivo (`[versions]` + `[libraries]`),
  e `implementation(libs.biometric)` em `app/build.gradle.kts`.
- `booleanPreference("travaDeAbertura", false)` — **padrão desligado**, ligado por ele nos Ajustes.
  Ligar exige uma autenticação de confirmação na hora (senão ele se tranca fora por engano).
- Um portão em `MainActivity.onCreate`, **antes** do `AnimatedNavHost`: enquanto não autenticado,
  compõe uma tela de bloqueio, não o `startDestination`. Não navegue para uma rota de bloqueio —
  a pilha de navegação (`reimagined-navigation`) permite voltar; o portão tem de estar **acima** dela.
- `BiometricPrompt.authenticate(promptInfo, CryptoObject(cipher))` com o `cipher` da chave mestra da
  §5.2 — **é a mesma autenticação**, não duas. Se você fizer um prompt só de UI e outro para a chave,
  o desenho perdeu o sentido.
- `setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)` — o PIN do aparelho serve de
  alternativa quando a digital falha. Não escreva tela de PIN própria.
- Retravar ao voltar do segundo plano depois de um tempo. Chave nos Ajustes: **imediato / 1 min / 5 min**.
  Use o ciclo de vida (`ON_STOP` grava o instante), não um timer solto.

### 6.3 Armadilhas

- **Não guarde um booleano "autenticado" no DataStore.** Ele sobrevive à morte do processo e vira
  trava que nunca fecha. O estado de autenticação é de memória e morre com o processo.
- `MainActivity` tem `launchMode="singleTop"` e trata `onNewIntent` (`MainActivity.kt:103`) para o
  retorno do OAuth. **Confirme que o portão não engole esse intent** — se engolir, o setup por OAuth
  para de funcionar e o sintoma vai parecer problema de rede.
- O `LessonHistoryWorker` roda com o app fechado (`MyApp.kt:24`). Ele não usa `Cred` (§2.4), então
  **não deve** passar pelo portão. Se você travá-lo, o histórico de aulas some sem mensagem de erro.
- `runBlocking { vm.tryInit() }` em `MainActivity.kt:62` roda na composição. Não acrescente leitura
  bloqueante de Keystore nesse caminho.

---

## 7. Fatia 4 — chave de assinatura própria — **NÃO EXECUTE SEM O "SIM" DELE**

É o buraco maior (§2.2) e o único com custo real para o Bruno.

**O custo:** trocar a chave quebra a atualização in-place. Ele precisa **desinstalar e reinstalar**, e
reconfigurar o repositório no app. Se estiver em `StorageConfig.App`, o clone se perde junto (é um
clone, não a fonte — mas qualquer edição não sincronizada morre com ele). **Antes de qualquer coisa,
ele tem de sincronizar o vault pelo app.**

O que fazer quando ele autorizar:

1. Gerar uma chave nova **fora do repo**, guardada por ele em lugar durável (perder a chave = nunca
   mais atualizar in-place).
2. `KEY_ALIAS` / `KEY_PASSWORD` / `STORE_PASSWORD` + o `.jks` em Base64 como **GitHub Secrets**; o
   workflow reconstitui o arquivo no runner. O `signingConfigs.release` já lê dessas variáveis de
   ambiente (`app/build.gradle.kts:80-86`) — o caminho já existe.
3. `fork-release.yml` passa a montar a variante assinada com a chave nova.
4. **Remover `app/nightly-signing-key.jks` do histórico do git**, não só do HEAD. Enquanto estiver no
   histórico de um repo público, continua baixável.
5. Refazer o **client OAuth Android do Drive** no Google Cloud (projeto `life-so-507017`): o SHA-1
   muda, e sem isso o Drive volta a dizer "Autorização cancelada". Se o sufixo do `applicationId`
   mudar, é outro client ainda.

**Alternativa que ele pode preferir, e que é mais barata:** tornar o fork **privado**. A GPL-3.0 obriga
a fornecer o fonte a quem recebe o binário — ele é o único que recebe. Repo privado + release privada
+ Obtainium com PAT resolve a exposição da chave sem reinstalação nenhuma. **Apresente as duas opções
e espere a escolha dele. Não escolha por ele.**

---

## 8. Fatia 5 — portão de segurança no CI

Acrescente um job `security` ao `fork-release.yml`, no mesmo padrão dos outros (ele já tem
`rust-tests` e `build` em paralelo com `publish` dependendo dos dois).

- **`mobsfscan`** sobre o código Kotlin (semgrep + regras), saída **SARIF** para o code scanning do
  GitHub. É o que roda em pipeline; o MobSF completo é servidor e não cabe aqui.
- **Na primeira rodada, não bloqueie.** É base do upstream: vai acusar dezenas de coisas que não são
  deste handoff. Gere o relatório, **commite a linha de base junto com o `RESULTADO_09`**, e só então
  decida com o Bruno o que vira portão. Portão que nasce vermelho é portão que alguém desliga.
- Não ponha `publish` dependendo de `security` nesta rodada.

---

## 9. Testes obrigatórios

O volume vai no núcleo puro. Nomes em português, como o resto do projeto.

### 9.A — cofre (`test/.../data/CofreDeCredenciaisTest.kt`) — é onde tem que estar o volume

1. Ida e volta: `selar` → `abrir` devolve exatamente a string original, incluindo string vazia,
   string com acento, e uma chave SSH ed25519 inteira com quebras de linha.
2. **IV nunca se repete:** cifrar o mesmo valor duas vezes produz ciphertexts diferentes.
3. **Adulteração é detectada:** virar um bit do ciphertext faz `abrir` falhar (tag GCM), não devolver
   lixo silenciosamente.
4. Envelope selado por uma `ChaveMestra` não abre com outra.
5. Base64 malformado no DataStore falha com erro tratado, não com exceção crua na UI.

### 9.B — migração (`test/.../data/MigracaoDeCredenciaisTest.kt`)

1. Preferências com valores em claro → depois da migração, **as chaves antigas não existem mais** no
   `Preferences`, e as novas abrem com o valor certo. Asserção sobre a ausência da chave, não sobre
   ela estar vazia.
2. `appAuthToken` é apagado junto.
3. Migração é **idempotente**: rodar duas vezes não corrompe nem re-cifra o já cifrado.
4. Migração interrompida no meio (simule falha entre gravar o novo e apagar o velho) **não perde a
   credencial** — na próxima abertura ainda dá para sincronizar.

### 9.C — portão de abertura (`test/.../ui/PortaoDeSegurancaTest.kt`, Robolectric)

1. Com `travaDeAbertura = false`, a tela inicial é a de sempre.
2. Com `travaDeAbertura = true` e sem autenticação, o conteúdo das notas **não é composto**
   (não basta estar coberto — asserte a ausência do nó).
3. Depois de autenticar, compõe o `startDestination` correto (Home, ou Setup se não iniciado).
4. Retravar por tempo: com "imediato", voltar do `ON_STOP` exige nova autenticação.
5. Aparelho sem bloqueio de tela: o app abre normalmente e a tela de Ajustes informa que a proteção
   está indisponível.

### 9.D — o que só o Bruno pode medir (roteiro no `RESULTADO_09`)

Isto **não** é teste automatizado — é roteiro numerado para ele executar no aparelho e reportar:

1. Ligar a trava, fechar o app, reabrir: pediu biometria?
2. Cancelar a biometria: o app **não** mostra nenhuma nota?
3. Autenticar e sincronizar o vault: o git ainda funciona (prova de que o cofre não quebrou a SSH)?
4. Tela de recentes com `FLAG_SECURE` ligado: aparece tarja/cinza no lugar da nota?
5. Enviar uma aula pelo Drive com o app fechado: o histórico continua atualizando (prova de que o
   portão não travou o `LessonHistoryWorker`)?
6. `adb backup` do pacote: tenta e **não sai nada** do app.

---

## 10. O que NÃO fazer

- **Não escreva criptografia à mão.** Nada de XOR, nada de AES/ECB, nada de IV fixo, nada de chave
  derivada de string no código.
- **Não use `androidx.security:security-crypto`** (deprecado em abril/2025) nem adicione Tink para
  quatro strings curtas.
- **Não guarde a chave de dados em disco em claro**, nem em `SharedPreferences`, nem em log.
- **Não escreva tela de PIN própria.** O `DEVICE_CREDENTIAL` do `BiometricPrompt` já é o PIN do
  aparelho, e é verificado pelo sistema, não por você.
- **Não faça a trava ser só UI.** Se as credenciais continuarem legíveis sem autenticar, o trabalho
  não foi feito — só ficou parecido com feito.
- **Não mexa no `applicationId`** (instalaria lado a lado e orfanaria a instalação atual).
- **Não execute a fatia 4** (chave de assinatura) sem autorização explícita dele.
- **Não toque no fluxo do Drive** (`aulas/`) — está correto e é a parte que mais custou a funcionar.
- **Não redesenhe o armazenamento** por causa da §3. Meça, reporte, espere.
- **Não misture as fatias num commit só.** Se a trava der errado, ele precisa poder reverter só ela.

---

## 11. Entrega

Commits pequenos e separados por fatia (1.1, 1.2, 1.3, 2, 3, 5), na `master`. O `fork-release.yml`
roda sozinho no push e o log lista cada teste executado.

No fim, escreva **`RESULTADO_09_SEGURANCA.md` no repositório**, contendo:

1. O valor medido do `StorageConfig` (§3) e o que ele implica.
2. A confirmação (ou a derrubada) de que os três call sites de `prefs.cred()` são de primeiro plano
   (§2.4) — é a premissa que sustenta a autenticação por uso.
3. O que a migração (§5.3) fez no aparelho dele: quantas chaves migradas, quais removidas.
4. A linha de base do `mobsfscan` (§8): quantos achados, quantos são deste handoff, quantos são do
   upstream, e a recomendação sobre o que virar portão.
5. O que ficou de fora e por quê — em particular, o estado da fatia 4 e da cifragem das notas.
6. O **roteiro manual numerado** da §9.D, com o que ele deve ver em cada passo.

Conclusão sem número medido não conta.
