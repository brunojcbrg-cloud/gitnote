# RESULTADO 10 — FASE I no app (I.2: imagem no modo leitura)

Entregue em 2026-09-21, commit `d2be3ac`, **release b75 (26.08.1.75)**,
[run 35550686128](https://github.com/brunojcbrg-cloud/gitnote/actions/runs/35550686128)
— os quatro jobs verdes na primeira rodada.

Escopo: **só o modo leitura**. I.3 (colar/escolher da galeria), I.5 e I.6
(redimensionar) não foram feitos — ver "O que ficou de fora".

---

## O ponto de partida, conferido antes de escrever código

| Alegação do prompt | Verificação |
|---|---|
| `markdownHelper.kt` passa `NoOpImageTransformerImpl()` | confirmado, agora na **linha 75** |
| Não há Coil/Glide/Picasso | confirmado (grep em `libs.versions.toml` e `app/build.gradle.kts`) |
| `![[...]]` não é sintaxe GFM | confirmado, e há mais: `MarkdownScanner.kt:196-198` **recusa de propósito** abrir wikilink quando o caractere anterior é `!` |
| SAF não é risco | confirmado: `FileSystem.kt` usa `java.nio.file.Paths`; o `.png` é lido pelo mesmo caminho do `.md` |

**Sobre o artefato de imagem pronto:** não existe. Conferido no código-fonte da
tag `v0.43.0`: os módulos publicados são `-m2`, `-m3`, `-coil2`, `-coil3` e
`-code`; `multiplatform-markdown-renderer-android` é só a variante Android do
módulo base (publicação KMP), não um módulo de imagem. Usar o pronto significaria
somar **Coil inteiro** — pilha de rede incluída — a um app que só lê arquivo
local. O transformador à mão ficou em **176 linhas** com amostragem, cache e
espaço reservado, abaixo do limite de ~150 linhas "mais casos de borda" que o
handoff deu como critério de troca. Nenhuma dependência nova entrou.

Há um ganho de segurança nisso que não estava previsto: como o transformador só
entende o endereço que o pré-passe gera, **não existe caminho pelo qual uma URL
remota chegue a ser buscada**, nem por engano nem por nota mal formada.

---

## O que foi feito

### `Anexos.kt` — a metade pura (JVM, sem Android)

Acha o embed, resolve o arquivo e reescreve o trecho:

```
![[Pasted image 20260920093913.png|496]]
  →  ![Pasted image 20260920093913.png](gitnote://image?path=06_Conhecimento%2F_anexos%2F…&w=496)
```

- **Resolução** na mesma ordem da web (`src/anexos.ts`): caminho exato, caminho
  sem diferenciar caixa, nome dentro da pasta de anexos, nome em qualquer lugar.
- **Largura**: `|496` e `|800x600` (lê a largura, **nunca** gera a altura);
  apelido não numérico vira texto alternativo.
- **Varredura própria**, e não o `MarkdownScanner`: ele é o mesmo scanner do modo
  de edição, onde o custo por tecla foi medido na Fase H. Somar um tipo novo lá
  mexeria na prévia ao vivo, que não é o escopo desta fase. A varredura nova pula
  cerca de código e código em linha.
- A listagem da pasta tolera a **Parte V**: se `06_Conhecimento/_anexos` não
  existir, procura `_anexos` sob qualquer pasta de topo. A renomeação não vai
  exigir mudança de código aqui.

### `ImagensDaNota.kt` — a metade Android

- **Amostragem obrigatória**: duas passadas de `BitmapFactory`
  (`inJustDecodeBounds` e depois `inSampleSize`), potência de dois, nunca abaixo
  da largura pedida. 4000×3000 cru são 48 MB de bitmap; três fotos numa nota
  matariam o app.
- **Cache LRU com teto em bytes** (1/8 da memória do processo), chave =
  caminho + largura alvo + data e tamanho do arquivo. Colar por cima do mesmo
  nome não devolve a imagem antiga.
- **Decodificação em `Dispatchers.IO`** via `produceState`, com espaço reservado
  (`placeholderConfig`) do tamanho final para a lista não pular enquanto carrega.
- Largura declarada manda; sem ela, largura natural limitada pela do contêiner —
  é o que `max-width: 100%` faz na web.

### Ligação no modo leitura (`MarkDown.kt`)

O pré-passe roda depois de `preprocessWikilinksForReading`, no mesmo `remember`
que já produzia `renderedContent`. **Nota sem `![` não paga nada**: nem a
listagem da pasta no disco, nem a leitura bloqueante da raiz do repositório —
nesse caso o transformador continua sendo o `NoOp`.

---

## Decisões que valem registrar

**Imagem remota sai escapada, não crua.** A decisão I.7 é "continua como texto",
e copiar o trecho cru **não** entrega isso: `![alt](https://…)` cru vira um nó de
imagem de verdade no parser GFM, o transformador o recusa, e o trecho **some da
tela sem aviso nenhum** — que é o comportamento de hoje, e é pior que mostrar o
texto. Escapado (`\!\[alt\]…`), ele aparece. Pelo mesmo motivo saem escapados o
anexo que não resolveu e o embed de nota (`![[Outra Nota]]`).

**O app resolve um caso que a web não resolve.** `![](06_Conhecimento/Medicina/foto.png)`,
com caminho completo **fora** da pasta de anexos, renderiza no app (o arquivo é
conferido no disco) e não renderiza na web (lá a lista de candidatos é só a pasta
de anexos). É superconjunto, não contradição: o texto gravado na nota é o mesmo
nos três clientes. Caminho que começa com `/` ou contém `..` é recusado.

**Uma divergência conhecida, não consertada de propósito:** o handoff (tabela
I.1) prevê `![alt|496](caminho.png)` a 496 px. **Nem a web nem o app fazem
isso** — na web o rótulo inteiro (`alt|496`) falha no teste de largura e vira
texto alternativo, e o app copia esse comportamento. Corrigir nos dois clientes
ao mesmo tempo é uma decisão do Bruno; corrigir só aqui seria a regressão que
esta fase veio impedir. O Obsidian aceita a forma.

---

## Provas

**448 testes `PASSED`, 0 `FAILED`** no job "Unit tests + APK" do run 35550686128.
A rodada anterior (`f87200e`) teve **410**: são exatamente **+38**, e nenhum dos
410 caiu.

Os 38 estão nomeados no log. Por critério de aceitação:

| Critério do prompt | Teste |
|---|---|
| nome curto acha em `_anexos` | `nomeCurtoAchaOArquivoNaPastaDeAnexos` |
| caixa e acento não importam | `caixaEAcentoNaoAtrapalham` |
| caminho completo vale | `caminhoCompletoVale`, `caminhoCompletoNoDiscoValeMesmoForaDaPastaDeAnexos` |
| nome inexistente devolve nulo e a nota não quebra | `nomeInexistenteDevolveNulo`, `nomeInexistenteNaoQuebraANota` |
| `\|496` e `\|800x600` | `larguraDaNotaChegaAoEndereco`, `larguraPorAlturaNaoGeraAltura` |
| apelido não numérico vira alt | `apelidoNaoNumericoViraTextoAlternativo` |
| imagem remota NÃO vira imagem | `imagemRemotaNaoViraImagem`, `dataUrlNaoViraImagem`, `imagemRemotaEEscapadaParaAparecerComoTexto` |
| Robolectric: o embed chega ao transformador | `oEmbedResolvidoChegaAoTransformadorComALarguraDaNota` |
| os 380+ testes atuais continuam verdes | 410 → 448 |

O teste Robolectric compõe `MarkdownCustomInner` **com o anotador de produção no
meio** (`missingWikilinkAnnotator`, o mesmo do modo leitura) e confere que o
endereço que chega ao transformador falso traz caminho e largura 300. Sem o
anotador de produção, um anotador que engolisse o nó de imagem passaria
despercebido. `LocalInspectionMode provides true` pelo motivo já medido em
`MarkdownCustomInnerRecolherTest`.

Os outros dois testes de composição fecham o cerco pelo lado negativo: imagem
remota **não** chega ao transformador e continua visível como texto; nota sem
imagem não chama o transformador nenhuma vez.

---

## O que NÃO foi medido

- **Nada disto foi visto em aparelho.** Nenhum teste aqui desenha um PNG de
  verdade: o transformador real (`decodificarAnexo`, `BitmapFactory`) não é
  exercitado por nenhum teste — o que os testes provam é que o embed chega até
  ele com o caminho e a largura certos. **Este é o passo que depende do Bruno.**
- **`PERF_IMAGEM`** (nota com 10 imagens, tempo e pico de memória) **não foi
  feito**: sem decodificação real no teste, o número seria inventado. Fica para
  a sessão que medir em aparelho ou que escrever um teste Robolectric com PNG
  sintético.
- **Teste do `.gitignore`** (gravar anexo em repositório onde a pasta está
  ignorada tem de avisar): pertence a I.3, que grava arquivo. Não há gravação
  nesta entrega.
- **Qual `StorageConfig` a instalação do Bruno usa** não foi conferido — não deu
  para conferir daqui. Os dois caminhos usam `java.nio`, então a leitura do
  `.png` segue a mesma regra da leitura do `.md`; se a nota abre, o anexo ao lado
  também abre.

---

## O que ficou de fora, e por quê

- **Modo de edição (prévia ao vivo)**: `VisualTransformation` não hospeda
  composable — ela transforma `AnnotatedString` em `AnnotatedString`. Não há
  como pôr uma imagem lá sem a **Fase J** (`TextFieldState`). O embed continua
  como texto no editor, que é o limite honesto desta arquitetura.
- **I.3 (colar e escolher da galeria)** e **I.6 (redimensionar)**: sessões
  próprias. I.6 no app é a folha com predefinições e controle deslizante no modo
  leitura, não alça de arrasto.
- Fase J, F.3/H.4 e Parte V: intocados, como mandava o prompt.

---

## Para o ESTADO_10

| Fase | Status | Data | Commit | Release |
|---|---|---|---|---|
| I.2 (app, leitura) | entregue; falta ver em aparelho | 2026-09-21 | d2be3ac | b75 (26.08.1.75) |
