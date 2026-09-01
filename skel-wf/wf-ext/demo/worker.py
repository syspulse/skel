"""
Temporal worker for the wf-ext Python demo.

Usage: worker.py [mode] [namespace]
  mode       (1st CLI arg, or MODE env; default "dynamic"):
    dynamic  register Demo + Demo-Human AND a dynamic catch-all (any other WorkflowType)
    static   register Demo + Demo-Human only (plus extra names in WORKFLOW_NAME)
  --check    print registered WorkflowTypes for both modes and exit (no Temporal connection)
  namespace  (2nd CLI arg, or TEMPORAL_NAMESPACE/NAMESPACE env; default "default"):
             the Temporal namespace this worker connects to. It MUST match the namespace wf-ext starts
             the workflow in (the --engine=temporal://host/<ns> namespace, or the start `ns`).

Polls TASK_QUEUE and runs DemoWorkflow / DemoHumanWorkflow + DemoStart/DemoWork/DemoHuman/DemoReport.
Defaults to a local Temporal dev server (localhost:7233 / namespace 'default'); override via env
for a hosted server.

Env:
  MODE                 dynamic | static        (default dynamic; overridden by the 1st CLI arg)
  WORKFLOW_NAME        extra static type names, CSV (Demo and Demo-Human are always registered)
  TEMPORAL_TARGET      gRPC host:port           (default localhost:7233)
  TEMPORAL_NAMESPACE   namespace                (default default; NAMESPACE is an alias; 2nd CLI arg wins)
  TASK_QUEUE           task queue to poll       (default DEMO_WORKFLOW_QUEUE)
  TEMPORAL_TLS         "1"/"true" to enable TLS (default off)
  TEMPORAL_AUTH_TOKEN  JWT -> "authorization: Bearer <token>" gRPC metadata (optional)
  WF_EXT_URL           WorkflowConfig API base  (default http://localhost:8080/api/v1/wf/ext)  [activities]
  WF_EXT_TOKEN         bearer token for WF_EXT_URL (optional)                                  [activities]
"""
import asyncio
import logging
import os
import sys

from temporalio.client import Client
from temporalio.worker import Worker

from activities import demo_start, demo_work, demo_report, demo_human
from demo_workflow import DemoWorkflowDynamic, STATIC_WORKFLOWS, WORKFLOW_NAMES
from temporalio.workflow import _Definition as _WfDef

TARGET = os.environ.get("TEMPORAL_TARGET", "localhost:7233")
TASK_QUEUE = os.environ.get("TASK_QUEUE", "DEMO_WORKFLOW_QUEUE")
REQUIRED_TYPES = ("Demo", "Demo-Human")


def resolve_mode() -> str:
    mode = (sys.argv[1] if len(sys.argv) > 1 else os.environ.get("MODE", "dynamic")).strip().lower()
    if mode not in ("dynamic", "static"):
        sys.exit(f"invalid mode '{mode}' (use: dynamic | static)")
    return mode


def resolve_namespace() -> str:
    # 2nd CLI arg wins, else TEMPORAL_NAMESPACE, else NAMESPACE alias, else "default"
    if len(sys.argv) > 2 and sys.argv[2].strip():
        return sys.argv[2].strip()
    return (os.environ.get("TEMPORAL_NAMESPACE") or os.environ.get("NAMESPACE") or "default").strip()


def workflows_for_mode(mode: str):
    """Workflow classes this worker registers. Demo and Demo-Human are always included."""
    workflows = list(STATIC_WORKFLOWS)
    if mode != "static":
        workflows.append(DemoWorkflowDynamic)
    return workflows


def registered_type_names(mode: str) -> list:
    names = []
    for cls in workflows_for_mode(mode):
        dfn = _WfDef.must_from_class(cls)
        names.append("<dynamic>" if getattr(dfn, "name", None) in (None, "") else dfn.name)
    return names


def served_label(mode: str) -> str:
    types = ", ".join(WORKFLOW_NAMES)
    if mode == "static":
        return f"static: WorkflowTypes {types}"
    return f"Demo + Demo-Human + dynamic (any other type)"


async def main():
    logging.basicConfig(level=logging.INFO)
    mode = resolve_mode()
    namespace = resolve_namespace()

    tls = os.environ.get("TEMPORAL_TLS", "").lower() in ("1", "true", "yes")
    token = os.environ.get("TEMPORAL_AUTH_TOKEN", "").strip()
    rpc_metadata = {"authorization": f"Bearer {token}"} if token else {}

    client = await Client.connect(
        TARGET, namespace=namespace, tls=tls, rpc_metadata=rpc_metadata,
    )

    # Always register Demo + Demo-Human. dynamic mode also adds a catch-all for any other type.
    workflows = workflows_for_mode(mode)
    served = served_label(mode)
    missing = [t for t in REQUIRED_TYPES if t not in registered_type_names(mode)]
    if missing:
        raise RuntimeError(f"worker does not register required WorkflowTypes: {missing}")

    worker = Worker(
        client,
        task_queue=TASK_QUEUE,
        workflows=workflows,
        activities=[demo_start, demo_work, demo_report, demo_human],
    )
    logging.info("worker polling '%s' @ %s (ns=%s, tls=%s) [%s]", TASK_QUEUE, TARGET, namespace, tls, served)
    await worker.run()


def check_types() -> None:
    """Print registered WorkflowTypes for both modes; exit non-zero if Demo or Demo-Human is missing."""
    ok = True
    for mode in ("dynamic", "static"):
        names = registered_type_names(mode)
        missing = [t for t in REQUIRED_TYPES if t not in names]
        status = "OK" if not missing else f"MISSING {missing}"
        print(f"mode={mode:8} types={names}  {status}")
        if missing:
            ok = False
    acts = ["DemoStart", "DemoWork", "DemoHuman", "DemoReport"]
    print(f"activities={acts}")
    if not ok:
        sys.exit(1)
    print("ok: Demo and Demo-Human are registered in both modes")


if __name__ == "__main__":
    if sys.argv[1:2] == ["--check"]:
        check_types()
    else:
        asyncio.run(main())
