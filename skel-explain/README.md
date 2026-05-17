# skel-explain

Service that generates human-readable (Markdown) explanations for input data using a configurable ScriptFlow processing engine.

## Concept

Rules are keyed by `(oid, rid)`:
- `oid` — owner ID (tenant). `oid=""` is the **default** rule, used as fallback
- `rid` — rule ID (e.g. detector name)

Each rule contains a **ScriptFlow**: an ordered list of script URIs. Scripts are chained — the output of each becomes the input of the next. The final output is the explanation (Markdown string).

**Lookup priority:** OID-specific rule → default rule (`oid=""`) → error

**Store structure:**
```
Map[oid, Map[rid, ScriptFlow]]
```

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
| `POST` | `/rule/{rid}` | admin/service | Create/update default rule |
| `GET` | `/rule/{rid}` | admin/service | Get default rule |
| `PUT` | `/rule/{rid}` | admin/service | Update default rule |
| `DELETE` | `/rule/{rid}` | admin/service | Delete default rule |
| `POST` | `/{oid}/{rid}` | own oid / admin | Create/update OID rule |
| `GET` | `/{oid}/{rid}` | own oid / admin | Get OID rule |
| `PUT` | `/{oid}/{rid}` | own oid / admin | Update OID rule |
| `DELETE` | `/{oid}/{rid}` | own oid / admin | Delete OID rule |
| `POST` | `/{rid}` | none | **Explain** — run ScriptFlow on input data |

### Request / Response

**Create rule request:**
```json
{
  "scripts": ["js://...", "ai://..."],
  "name": "optional name"
}
```

**Explain request:**
```json
{
  "oid": "490",
  "schema": { "type": "object", "properties": { "address": { "type": "string" } } },
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
| `js` | JavaScript (GraalVM) | JS expression; `input` is the incoming string | — |
| `ai` | LLM (ScriptAI) | Prompt template; `${input}` substituted | AI model URI, e.g. `openai://gpt-4o` |
| `jq` | jq | JSON query expression | — |
| `regexp` | Regex | Match / extract pattern | — |
| `str` | Passthrough | ignored | — |

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

**With ScriptAI (js → ai):**
```json
{
  "scripts": [
    {"typ": "js", "src": "var d=JSON.parse(input); var m=d.metadata; JSON.stringify({wallet:m.wallet,balance:m.balance,threshold:m.threshold})"},
    {"typ": "ai", "src": "Explain this wallet alert concisely: ${input}", "opts": "openai://gpt-4o"}
  ]
}
```

## Rule Files

Rules are stored as JSON files in the `rules/` directory. Each file is a valid `ExplainRuleCreateReq`:

```json
{
  "name": "My Rule",
  "scripts": [
    {"typ": "js", "src": "var d=JSON.parse(input); var m=d.metadata; 'Balance: ' + m.balance"},
    {"typ": "js", "src": "'Result: ' + input"}
  ]
}
```

Bundled examples:

| File | Description |
|---|---|
| `rules/DetectorWallet.json` | Default wallet balance explanation (JS) |
| `rules/DetectorWallet-oid490.json` | OID-490 override (JS) |
| `rules/DetectorWallet-chain.json` | Two-step chained JS flow |
| `rules/DetectorWallet-updated.json` | Updated variant (used in demo) |
| `rules/DetectorWallet-ai.json` | JS pre-processing + GPT-4o explanation (Test-1.md) |

## Shell Scripts

All scripts use `SERVICE_URI` (default `http://127.0.0.1:8080/api/v1/explain`) and `ACCESS_TOKEN` env vars.

### Default rules (admin/service only)

```bash
# Create — $2 is path to rule file (default: rules/<rid>.json)
./exp-rule-create.sh <rid> [rule-file]
./exp-rule-create.sh DetectorWallet
./exp-rule-create.sh DetectorWallet rules/DetectorWallet.json

# Get
./exp-rule-get.sh <rid>
./exp-rule-get.sh DetectorWallet

# Update — $2 is path to rule file (optional; omit to update only NAME)
./exp-rule-update.sh <rid> [rule-file]
./exp-rule-update.sh DetectorWallet rules/DetectorWallet-updated.json
NAME="New Name" ./exp-rule-update.sh DetectorWallet

# Delete
./exp-rule-delete.sh <rid>
```

### OID-specific rules

```bash
# Create (OID env var, default 490) — $2 is path to rule file
OID=490 ./exp-create.sh <rid> [rule-file]
OID=490 ./exp-create.sh DetectorWallet rules/DetectorWallet-oid490.json

# Get
OID=490 ./exp-get.sh <rid>

# Update — $2 is path to rule file (optional)
OID=490 ./exp-update.sh <rid> [rule-file]
OID=490 ./exp-update.sh DetectorWallet rules/DetectorWallet-updated.json

# Delete
OID=490 ./exp-delete.sh <rid>
```

### Explain

```bash
# Using default rule (no oid)
./exp-explain.sh <rid> '<data-json>'

# Using OID-specific rule (falls back to default if not found)
OID=490 ./exp-explain.sh <rid> '<data-json>'

# With explicit schema
OID=490 SCHEMA='{"type":"object"}' ./exp-explain.sh DetectorWallet '{"metadata":{"tx_from":"0xABC","balance":100}}'
```

### Demo

Runs the full lifecycle (create → update → explain default/oid/fallback → delete):

```bash
./exp-demo.sh
# custom oid/rid:
OID=123 RID=MyDetector ./exp-demo.sh
```

## Example: DetectorWallet

### 1. Create default rule

```bash
./exp-rule-create.sh DetectorWallet
# uses rules/DetectorWallet.json
```

### 2. Create OID-specific override for oid=490

```bash
OID=490 ./exp-create.sh DetectorWallet rules/DetectorWallet-oid490.json
```

### 3. Explain (no oid — uses default)

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

**Output:**
```
Sender [0xA911Ff351B143634Dbc5aF3E204EA074583A83e3](https://etherscan.io/address/0xa911ff...) 
triggered balance drop on [0x9000...], balance: 100, threshold: > 1000.0
```

### 4. Explain for oid=490 (uses OID rule)

```bash
OID=490 ./exp-explain.sh DetectorWallet '{"metadata":{"tx_from":"0xABC","balance":50,"threshold":">100","wallet":"0x900"}}'
```

### 5. Explain for oid=999 (no rule → fallback to default)

```bash
OID=999 ./exp-explain.sh DetectorWallet '{"metadata":{"tx_from":"0xABC","balance":50,"threshold":">100","wallet":"0x900"}}'
# oid in response will be "" (the default rule was used)
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
- `ExplainRoutesSpec` — full HTTP: CRUD auth, explain fallback, ScriptJS, ScriptFlow chain, oid override
