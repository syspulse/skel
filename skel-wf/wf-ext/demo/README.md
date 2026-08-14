# wf-ext Python demo (3 activities, config-by-cid)

A minimal Python Temporal worker that runs `DemoWorkflow` → `DemoStart` → `DemoWork` → `DemoReport`.
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

> To try it: add a detector named `DemoHuman` to the `Demo` schema (UI/DSL), start it, watch the worker
> log "waiting for the 'CONTINUE' signal", optionally edit the DemoHuman DetectorConfig, then send the
> signal and see `DemoHuman` print the (re-read) config.

## Files

| file               | purpose                                                          |
|--------------------|------------------------------------------------------------------|
| `activities.py`    | `DemoStart` (returns topology)/`DemoWork`/`DemoHuman`/`DemoReport` + the WorkflowConfig/DetectorConfig client |
| `demo_workflow.py` | `DemoWorkflowDynamic` (any type) + `DemoWorkflow` (static, name `WORKFLOW_NAME`); reads Memo `cid`/`sid`, topology-aware, `CONTINUE` signal handler for the DemoHuman gate |
| `worker.py`        | connects to Temporal, polls `DEMO_WORKFLOW_QUEUE`; registers dynamic or static per mode |
| `run-worker.sh`    | venv + deps + run the worker (`[dynamic|static]`)               |
| `create-and-start.sh` | create the `Demo` schema and start it via the wf-ext API |

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
   ./run-worker.sh                    # dynamic (default): serves any schema/WorkflowType
   ./run-worker.sh static             # dedicated: serves ONLY WorkflowType == WORKFLOW_NAME (default Demo)
   ./run-worker.sh dynamic my-ns      # connect to a specific Temporal namespace (2nd arg)
   ```

4. **Create + start** a run:
   ```bash
   ./create-and-start.sh
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
| `MODE` | `dynamic` | `dynamic` (any type) or `static` (only `WORKFLOW_NAME`); 1st CLI arg overrides |
| `WORKFLOW_NAME` | `Demo` | static-mode registration name == served `WorkflowSchema.name` |
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

- **dynamic** (`@workflow.defn(dynamic=True)`) — serves *any* WorkflowType. One worker runs every
  schema. Convenient, no name coupling.
- **static** (`@workflow.defn(name=WORKFLOW_NAME)`) — serves *only* `WorkflowType == WORKFLOW_NAME`.
  This is how you dedicate a worker to a specific `WorkflowSchema`.

**For static mode, which name?** The registered name must equal the `WorkflowSchema.name` you want to
serve (that's what becomes the WorkflowType). Default `WORKFLOW_NAME=Demo`, so create the schema
with `name = "Demo"`. To dedicate a worker to another schema, set `WORKFLOW_NAME=<that schema's
name>`.

**Isolate it fully** by also giving each dedicated worker its own task queue and starting those schemas
onto it, e.g.:
```bash
# dedicated worker for schema "Demo" on its own queue
WORKFLOW_NAME=Demo TASK_QUEUE=DEMO_WORKFLOW_QUEUE ./run-worker.sh static
# start onto that queue (WorkflowType is the schema name)
./wf-schema-start.sh <schemaId>   # from skel-wf/wf-ext, with TASK_QUEUE=DEMO_WORKFLOW_QUEUE
```
A run whose type isn't `WORKFLOW_NAME` that lands on a static worker's queue fails fast with
`Workflow class <Type> is not registered` — the intended guardrail. The task queue must always match
between start (`?tq=`) and worker (`TASK_QUEUE`).
