# skel-explain

Service that generates human-readable (Markdown) explanations for input data using a configurable ScriptFlow processing engine.

## Concept

Rules are keyed by `(oid, rid)`:
- `oid` — owner ID (tenant), derived from the JWT bearer token
- `rid` — rule ID (e.g. detector name)

Each rule contains a **ScriptFlow**: an ordered list of script definitions. Scripts are chained — the output of each becomes the input of the next. The final output is the explanation (Markdown string).

**Lookup priority for explain:** caller's `oid` rule → default rule (`oid=""`) → error

## Running

```bash
cd skel-explain
./run-explain.sh --datastore=mem://
# with file persistence:
./run-explain.sh --datastore=dir://./store
# with postgres:
./run-explain.sh --datastore=postgres://mydb
```

**Key options:**

| Flag | Default | Description |
|---|---|---|
| `--http.port` | `8080` | HTTP listen port |
| `--http.host` | `0.0.0.0` | HTTP listen host |
| `--datastore` | `mem://` | Store backend (`mem://`, `dir://<path>`, `postgres://<db>`) |
| `--jwt.uri` | `hs512://` | JWT secret (`hs256://secret`, `rs512://pk/key`) |
| `--permissions` | `user` | Auth mode (`user`, `strict`) |
| `--admin.role` | `explain-admin` | Role name for admin |
| `--service.role` | `explain-service` | Role name for service accounts |

## API

Base path: `/api/v1/explain`

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/` | admin/service | List all rules (optional `?oid=` filter) |
| `DELETE` | `/` | own oid / admin | Delete **all** rules for default `oid=""` |
| `DELETE` | `/?oid={oid}` | own oid / admin | Delete **all** rules for the given oid |
| `POST` | `/{rid}` | any (oid from JWT) | Create Explain rule |
| `GET` | `/{rid}` | any (oid from JWT) | Get Explain rule |
| `PUT` | `/{rid}` | any (oid from JWT) | Update Explain rule |
| `DELETE` | `/{rid}` | any (oid from JWT) | Delete Explain rule |
| `GET` | `/{rid}/explain` | none | **Explain** — run ScriptFlow, optional `?style=` |

The `oid` for CRUD operations is always taken from the JWT `oid` claim. There is no `oid` in the URL.

### Explain styles

The optional `style` query parameter controls explanation verbosity. It is passed to scripts via the `style` variable in the script data map.

| Value | Description |
|---|---|
| `""` (default) | No style hint |
| `short` | Brief, single-sentence explanation |
| `narrative` | Prose narrative explanation |
| `detailed` | Full technical detail |

### Request / Response

**Create / Update rule request body:**
```json
{
  "scripts": [
    {"typ": "js", "src": "..."},
    {"typ": "ai", "src": "...", "opts": "openai://gpt-4o"}
  ],
  "name": "optional name",
  "desc": "optional description",
  "sid": "optional schema id"
}
```

**Explain request body** (optional on GET `/{rid}/explain`):
```json
{
  "oid": "490",
  "data": {
    "address": "0x9000000000000000000000000000000000000000",
    "metadata": {
      "tx_from": "0xA911Ff351B143634Dbc5aF3E204EA074583A83e3",
      "balance": 100,
      "threshold": "> 1000.0",
      "wallet": "0x9000000000000000000000000000000000000000"
    }
  }
}
```

**Explain response:**
```json
{
  "explanation": "Sender [0xA911...](https://etherscan.io/address/0xa911...) triggered balance change on [0x9000...] Balance: 100 threshold: > 1000.0",
  "ts": 1715769600000,
  "scripts": ["js"],
  "oid": ""
}
```

## Scripts

Each element of `scripts` is a `ScriptDef` object. Scripts are chained — output of each step is `input` for the next.

```json
{ "typ": "<engine>", "src": "<source or prompt>", "opts": "<optional, e.g. AI model URI>" }
```

| `typ` | Engine | `src` | `opts` |
|---|---|---|---|
| `js` | JavaScript (GraalVM) | JS expression; `input` is the incoming string; `style` is available | — |
| `ai` | LLM (ScriptAI) | Prompt template; `${input}` and `${style}` substituted | AI model URI, e.g. `openai://gpt-4o` |
| `jq` | jq | JSON query expression | — |
| `regexp` | Regex | Match / extract pattern | — |
| `str` | Passthrough | Returns src as-is | — |

**Single script:**
```json
{
  "scripts": [
    {"typ": "js", "src": "var d=JSON.parse(input); var m=d.metadata; 'Sender ['+m.tx_from+'] balance: '+m.balance"}
  ]
}
```

**Chained ScriptFlow (js → js):**
```json
{
  "scripts": [
    {"typ": "js", "src": "JSON.parse(input).metadata.balance.toString()"},
    {"typ": "js", "src": "'Balance is: ' + input"}
  ]
}
```

**With ScriptAI and style (js → ai):**
```json
{
  "scripts": [
    {"typ": "js", "src": "var d=JSON.parse(input); var m=d.metadata; JSON.stringify({wallet:m.wallet,balance:m.balance,threshold:m.threshold})"},
    {"typ": "ai", "src": "Explain this wallet alert (style: ${style}): ${input}", "opts": "openai://gpt-4o"}
  ]
}
```

**Style-aware JS script:**
```json
{
  "scripts": [
    {"typ": "js", "src": "style === 'short' ? 'Balance: ' + JSON.parse(input).metadata.balance : 'Sender ' + JSON.parse(input).metadata.tx_from + ' triggered balance change: ' + JSON.parse(input).metadata.balance"}
  ]
}
```

## Rule Files

Rules are stored as JSON files in the `rules/` directory. Each file is a valid `ExplainCreateReq`:

```json
{
  "name": "My Rule",
  "scripts": [
    {"typ": "js", "src": "var d=JSON.parse(input); var m=d.metadata; 'Balance: ' + m.balance"},
    {"typ": "js", "src": "'Result: ' + input"}
  ]
}
```

## Shell Scripts

All scripts use `SERVICE_URI` (default `http://127.0.0.1:8080/api/v1/explain`) and `ACCESS_TOKEN` env vars.

```bash
# Create rule (reads from rules/Rule-<rid>.json by default)
./exp-create.sh <rid> [rule-file]
./exp-create.sh DetectorWallet
./exp-create.sh DetectorWallet rules/MyRule.json
OID=490 ./exp-create.sh DetectorWallet rules/MyRule.json

# Get rule
./exp-get.sh <rid>
./exp-get.sh DetectorWallet

# Update rule
./exp-update.sh <rid> [rule-file]
./exp-update.sh DetectorWallet rules/DetectorWallet-updated.json
NAME="New Name" ./exp-update.sh DetectorWallet

# Delete rule
./exp-delete.sh <rid>
./exp-delete.sh DetectorWallet

# Explain
./exp-explain.sh <rid> '<data-json>'
./exp-explain.sh DetectorWallet '{"metadata":{"tx_from":"0xABC","balance":100}}'

# Explain with one or many alerts from an alert file (uses .data[0] for 1 alert, {data:[...],total:N} for 2+)
./exp-explain.sh DetectorTransferEvm alerts/Alert-DetectorTransferEvm-1.json
./exp-explain.sh DetectorTransferEvm alerts/Alert-DetectorTransferEvm-2.json

# Explain with style
STYLE=short ./exp-explain.sh DetectorWallet '{"metadata":{"tx_from":"0xABC","balance":100}}'
STYLE=narrative ./exp-explain.sh DetectorWallet '{"metadata":{"tx_from":"0xABC","balance":100}}'

# Explain with explicit oid (falls back to oid="" if no matching rule)
OID=490 ./exp-explain.sh DetectorWallet '{"metadata":{"tx_from":"0xABC","balance":100}}'

# Bulk-load rules from rules/*-default.json
./exp-ext-set.sh

# List rules (admin)
./exp-ext-get.sh

# Delete ALL rules for default oid=""
./exp-ext-clean.sh

# Delete ALL rules for a specific oid
OID=490 ./exp-ext-clean.sh
```

### Demo

Runs the full lifecycle (create → get → update → explain with styles → delete):

```bash
./exp-demo.sh
# custom rid:
RID=MyDetector ./exp-demo.sh
```

## Example: DetectorWallet

### 1. Create rule

```bash
./exp-create.sh DetectorWallet
# uses rules/Rule-DetectorWallet.json
```

### 2. Explain (no style)

```bash
./exp-explain.sh DetectorWallet '{
  "address": "0x9000000000000000000000000000000000000000",
  "metadata": {
    "tx_from": "0xA911Ff351B143634Dbc5aF3E204EA074583A83e3",
    "balance": 100,
    "threshold": "> 1000.0",
    "wallet": "0x9000000000000000000000000000000000000000"
  }
}'
```

### 3. Explain with style

```bash
STYLE=short ./exp-explain.sh DetectorWallet '{"metadata":{"tx_from":"0xABC","balance":100}}'
STYLE=narrative ./exp-explain.sh DetectorWallet '{"metadata":{"tx_from":"0xABC","balance":100}}'
STYLE=detailed ./exp-explain.sh DetectorWallet '{"metadata":{"tx_from":"0xABC","balance":100}}'
```

## Tests

```bash
# All tests
sbt "skel_explain/test"

# Specific suite
sbt "skel_explain/testOnly io.syspulse.skel.explain.ExplainRoutesSpec"
sbt "skel_explain/testOnly io.syspulse.skel.explain.ExplainStoreMemSpec"
sbt "skel_explain/testOnly io.syspulse.skel.explain.ExplainStoreDirSpec"
```

Test coverage:
- `ExplainStoreMemSpec` — store CRUD, composite key, oid isolation
- `ExplainStoreDirSpec` — file persistence, reload from disk, multi-script rules
- `ExplainRoutesSpec` — full HTTP: CRUD (oid from JWT), explain with style, ScriptJS, ScriptFlow chain, fallback
