# wf-ext Python demo (3 activities, config-by-cid)

A minimal Python Temporal worker that runs `Demo` or `Demo-Human` → `DemoStart` → `DemoWork` →
(`[DemoHuman]` when that detector is wired in) → `DemoReport`.
Each activity reads its own configuration from the **wf-ext WorkflowConfig API** using the
`WorkflowConfig.id` (`cid`) that wf-ext passes on start.

## How the `cid`/`sid` reach the worker

When wf-ext starts a workflow (`WorkflowAssembly.start` → `Engine.start`), it attaches two **top-level
Temporal Memo fields** (user metadata, JSON-encoded): `cid` (`WorkflowConfig.id`) and `sid`
(`WorkflowSchema.id`). Memo:

- rides **alongside** the input — it does not change the workflow's input contract,
- is visible in the Temporal Web UI,
- is readable by the worker's workflow code.

The workflow reads them with `workflow.memo_value("cid", 0, type_hint=int)` /
`workflow.memo_value("sid", 0, type_hint=int)` and forwards `cid` to every activity. Activities then
call `GET {WF_EXT_URL}/config/{cid}` to load the `WorkflowConfig` (including `meta.input`, the
start-input JSON wf-ext stored).

> On the wire the fields live under the Memo proto's `fields` map (`Memo{fields:{cid, sid}}`); every
> SDK exposes them as top-level `memo_value("cid")` / `memo_value("sid")`.
>
> Why Memo (not the input payload)? The input belongs to the workflow's own contract; `cid`/`sid` are
> orchestration metadata. Keeping them separate means the demo workflow's input stays whatever the
> caller sends, while `cid`/`sid` are always available regardless of input shape.

## Topology-aware flow + human-gated step (DemoHuman + signals)

`DemoStart` reads the WorkflowConfig **with its detectors** (`GET /config/{cid}?entity=detector`) and
returns the wired-in `DetectorConfig`s, so the workflow learns its **topology** without an extra call.
The flow adapts:

```
DemoStart -> DemoWork -> [ DemoHuman  (only if a "DemoHuman" detector is wired in) ] -> DemoReport
```

- **No `DemoHuman` detector** → runs straight through (as before).
- **`DemoHuman` detector present** → the workflow starts `DemoHuman` as a **long-running activity** so it
  is visible in the engine (and maps to the `DemoHuman` DetectorConfig node) while it waits. The wait
  loop only **heartbeats** — it does not read the config. The workflow registers a `CONTINUE` **signal**
  handler; when the signal arrives it **releases** (cancels) the activity, and **only then** does
  `DemoHuman` re-read its DetectorConfig fresh (`GET /detector/config/{id}`) and print it — so a config
  edit made *while waiting* is picked up at release time. Then the flow proceeds to `DemoReport`.

Send the signal (generic — any WorkflowConfig, any signal name, optional JSON payload):

```bash
# REST:  POST /config/{id}/signal?name=CONTINUE   body = JSON payload
../wf-config-signal.sh <configId>                       # signal CONTINUE with {}
../wf-config-signal.sh <configId> CONTINUE '{"ok":1}'   # with a payload
# CLI:   wf-ext signal <configId> [signalName=CONTINUE] [payloadJson]   (requires --engine.uri)
```

> To try it: create a `Demo-Human` schema (detectors include `DemoHuman`) and start it, or add a
> detector named `DemoHuman` to a `Demo` schema (UI/DSL). Watch the worker log "waiting for the
> 'CONTINUE' signal", optionally edit the DemoHuman DetectorConfig, then send the signal and see
> `DemoHuman` print the (re-read) config.
>
> ```bash
> ./create-and-start.sh human    # WorkflowType Demo-Human, DemoHuman in the graph
> ../wf-config-signal.sh <configId>
> ```

## Files

| file               | purpose                                                          |
|--------------------|------------------------------------------------------------------|
| `activities.py`    | `DemoStart` (returns topology)/`DemoWork`/`DemoHuman`/`DemoReport` + the WorkflowConfig/DetectorConfig client |
| `demo_workflow.py` | `DemoWorkflowDynamic` (any type) + static `Demo` and `Demo-Human`; reads Memo `cid`/`sid`, topology-aware, `CONTINUE` signal handler for the DemoHuman gate |
| `worker.py`        | connects to Temporal, polls `DEMO_WORKFLOW_QUEUE`; registers dynamic or static per mode |
| `run-worker.sh`    | venv + deps + run the worker (`[dynamic|static]`)               |
| `create-and-start.sh` | create `Demo` or `Demo-Human` (`./create-and-start.sh human`) and start it via the wf-ext API |

## Run it

1. **A Temporal server** (local dev):
   ```bash
   temporal server start-dev            # gRPC :7233, UI :8233
   ```

2. **wf-ext with an engine** pointed at that server:
   ```bash
   # from skel-wf/wf-ext — must include --engine.uri so start/resolve work
   ../../run-app.sh wf-ext io.syspulse.skel.wf.ext.App -- --engine.uri=temporal://
   ```

3. **The worker** (task queue `DEMO_WORKFLOW_QUEUE`):
   ```bash
   ./run-worker.sh                    # Demo + Demo-Human + dynamic catch-all (default)
   ./run-worker.sh static             # Demo + Demo-Human only
   ./run-worker.sh --check            # print registered WorkflowTypes and exit
   ./run-worker.sh dynamic my-ns      # connect to a specific Temporal namespace (2nd arg)
   ```

4. **Create + start** a run:
   ```bash
   ./create-and-start.sh              # WorkflowType Demo
   ./create-and-start.sh human        # WorkflowType Demo-Human (includes DemoHuman)
   ```

Each activity prints a START line (after a blank line) and an END line; `DemoWork` sleeps for the
input's `"n"` seconds. With the default input `{"type":"demo","work":"process","n":42}` you'll see:
```

[DemoStart] START cid=6
[DemoStart] END cid=6

[DemoWork] START cid=6
... sleeps n=42 seconds ...
[DemoWork] END cid=6 (slept 42s)

[DemoReport] START cid=6
[DemoReport] END cid=6
```
(The workflow gives `DemoWork` a `start_to_close_timeout` of `n + 30`s so the sleep never trips it.)

The workflow's return value is picked up by wf-ext's Resolve and stored back into
`WorkflowConfig.meta.result`.

## Config / env

Worker (`worker.py`):

| env | default | meaning |
|-----|---------|---------|
| `MODE` | `dynamic` | `dynamic` (any type) or `static` (`Demo` + `Demo-Human`); 1st CLI arg overrides |
| `WORKFLOW_NAME` | `Demo,Demo-Human` | extra static type names (CSV); `Demo` and `Demo-Human` are always registered |
| `TEMPORAL_TARGET` | `localhost:7233` | Temporal gRPC host:port |
| `TEMPORAL_NAMESPACE` | `default` | namespace (`NAMESPACE` alias; 2nd CLI arg `worker.py [mode] [namespace]` wins) |
| `TASK_QUEUE` | `DEMO_WORKFLOW_QUEUE` | task queue to poll |
| `TEMPORAL_TLS` | off | `1`/`true` to enable TLS |
| `TEMPORAL_AUTH_TOKEN` | — | JWT → `authorization: Bearer …` gRPC metadata |

Activities (`activities.py`) and `create-and-start.sh`:

| env | default | meaning |
|-----|---------|---------|
| `WF_EXT_URL` | `http://localhost:8080/api/v1/wf/ext` | WorkflowConfig API base |
| `WF_EXT_TOKEN` | — | bearer token for the wf-ext API |

## Restricting which schemas a worker runs (dynamic vs. static)

The Temporal Workflow *Type* equals `WorkflowConfig.name` (defaults to `WorkflowSchema.name`). The
worker chooses how it matches types:

- **dynamic** (`Demo` + `Demo-Human` named types, plus `@workflow.defn(dynamic=True)` catch-all) —
  named types take precedence; any other WorkflowType hits the catch-all.
- **static** (`@workflow.defn(name=...)`) — serves **`Demo` and `Demo-Human`** only (plus extra
  names from `WORKFLOW_NAME`). No catch-all.

**For static mode, which names?** The registered names must equal the `WorkflowSchema.name` values
you want to serve (that's what becomes the WorkflowType). Defaults:

| WorkflowType | Schema | Activities |
|--------------|--------|------------|
| `Demo` | 3-step demo | `DemoStart` → `DemoWork` → `DemoReport` |
| `Demo-Human` | same + human gate | `DemoStart` → `DemoWork` → **`DemoHuman`** (CONTINUE) → `DemoReport` |

`DemoHuman` still only *runs* when a detector named `DemoHuman` is wired into that run's graph
(topology-aware). `Demo-Human` is the schema name whose graph includes that detector.

To also serve another schema from the same static worker:
```bash
WORKFLOW_NAME=OtherSchema ./run-worker.sh static   # still also registers Demo and Demo-Human
```

**Isolate it fully** by also giving each dedicated worker its own task queue and starting those schemas
onto it, e.g.:
```bash
# dedicated worker for the demo types on their queue
TASK_QUEUE=DEMO_WORKFLOW_QUEUE ./run-worker.sh static
# start onto that queue (WorkflowType is the schema name)
./wf-schema-start.sh <schemaId>   # from skel-wf/wf-ext, with TASK_QUEUE=DEMO_WORKFLOW_QUEUE
```
A run whose type isn't among the registered static names that lands on this worker's queue fails fast
with `Workflow class <Type> is not registered` — the intended guardrail. The task queue must always
match between start (`?tq=`) and worker (`TASK_QUEUE`).
