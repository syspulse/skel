# wf-ext

Workflow extension service: schemas/configs, Assembly DSL, and Temporal Engine binding.

Base URI: `http://127.0.0.1:8080/api/v1/wf/ext`

## Run server

```
APP_EXEC=bloop ./run-wf.sh server
```

With Temporal Engine (enables `/temporal/*` and `/engine/*` routes):

```
APP_EXEC=bloop ./run-wf.sh --engine.uri=temporal://127.0.0.1:7233/default server
```

### Datastores

| URI | Store |
|-----|--------|
| `mem://` / `cache://` | In-memory (default) |
| `dir://` | JSON files under `store/` |
| `dir://path` | JSON files under `path` |
| `postgres://` | Postgres DB `postgres` |
| `postgres://db` | Postgres DB `db` |
| `jdbc://...` | Postgres via JDBC URI |

In-memory:

```
APP_EXEC=bloop ./run-wf.sh -d mem:// server
```

Directory (persisted JSON under `store/`):

```
APP_EXEC=bloop ./run-wf.sh -d dir://store server
```

Postgres (set `DB_*` from `db/postgres/db-env.sh`, or use defaults in `conf/application-wf.conf`):

```
source db/postgres/db-env.sh
APP_EXEC=bloop ./run-wf.sh -d postgres://workflow_db server
```

```
source db/postgres/db-env.sh
APP_EXEC=bloop ./run-wf.sh -d "jdbc://postgres?search=tgram" server
```

With engine + directory store:

```
APP_EXEC=bloop ./run-wf.sh -d dir://store --engine.uri=temporal://127.0.0.1:7233/default server
```

## Assembly DSL

Bracket shorthand `[X]` is rewritten to `Detector.X`. Full form also works:

```
[ProofOfOwnership] -> [ProofOfReserve] -> [Report] -> [Commit]
Detector.PoO -> Detector.PoR -> Detector.Report
```

- **assembly** — creates DetectorSchema/DetectorConfig (and WorkflowSchema/WorkflowConfig) from the pipeline.
- **link** — references **existing** DetectorConfigs by name (latest version); creates no Detector*; missing name fails.

## Assembly

### Command

```
APP_EXEC=bloop ./run-wf.sh -d dir://store assembly '[PoO] -> [PoR] -> [Report]'
```

Optional: `--wid=<id>`, `--wn=<name>` for WorkflowSchema id/name.

### API

Create WorkflowConfig (creates Detectors):

```
curl -s -X POST http://127.0.0.1:8080/api/v1/wf/ext/config/assembly \
  -H 'Content-Type: application/json' \
  -d '{"pipeline":"[A] -> [B] -> [C]"}'
```

Assemble and bind to a Temporal runtime (`id` = RunId UUID or WorkflowId). Server must be started with `--engine.uri`:

```
curl -s -X POST "http://127.0.0.1:8080/api/v1/wf/ext/temporal/assembly/<id>?ns=default" \
  -H 'Content-Type: application/json' \
  -d '{"pipeline":"[ProofOfOwnership] -> [ProofOfReserve] -> [Report] -> [Commit]"}'
```

Helper:

```
./wf-engine-assembly.sh <id> '[ProofOfOwnership] -> [ProofOfReserve] -> [Report] -> [Commit]'
```

## Link

### Command

Requires existing DetectorConfigs (e.g. from a prior `assembly`) and an engine:

```
APP_EXEC=bloop ./run-wf.sh -d dir://store --engine.uri=temporal:// link <workflowId|runtimeId> '[PoO] -> [PoR] -> [Report]'
```

Helper:

```
DATASTORE=dir://store ./run-wf-engine-link.sh 019e7473-05d1-789f-bb4b-44845bd69fc6 '[PoO] -> [PoR] -> [Report]'
```

### API

Link only (no Engine bind):

```
curl -s -X POST http://127.0.0.1:8080/api/v1/wf/ext/config/link \
  -H 'Content-Type: application/json' \
  -d '{"pipeline":"[ProofOfOwnership] -> [ProofOfReserve]"}'
```

Link and bind to Temporal (`id` = RunId UUID or WorkflowId):

```
curl -s -X POST "http://127.0.0.1:8080/api/v1/wf/ext/temporal/link/<id>?ns=default" \
  -H 'Content-Type: application/json' \
  -d '{"pipeline":"[ProofOfOwnership] -> [ProofOfReserve] -> [Report] -> [Commit]"}'
```

Helper:

```
./wf-engine-link.sh <id> '[ProofOfOwnership] -> [ProofOfReserve] -> [Report] -> [Commit]'
```
