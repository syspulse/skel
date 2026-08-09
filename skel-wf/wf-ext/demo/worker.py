"""
Temporal worker for the wf-ext Python demo.

Usage: worker.py [mode] [namespace]
  mode       (1st CLI arg, or MODE env; default "dynamic"):
    dynamic  register DemoWorkflowDynamic  -> runs ANY WorkflowType (one worker serves all schemas)
    static   register DemoWorkflow         -> runs ONLY WorkflowType == WORKFLOW_NAME (dedicated worker
             for a specific WorkflowSchema; combine with a dedicated TASK_QUEUE to fully isolate it)
  namespace  (2nd CLI arg, or TEMPORAL_NAMESPACE/NAMESPACE env; default "default"):
             the Temporal namespace this worker connects to. It MUST match the namespace wf-ext starts
             the workflow in (the --engine=temporal://host/<ns> namespace, or the start `ns`).

Polls TASK_QUEUE and runs DemoWorkflow + its 3 activities. Defaults to a local Temporal dev server
(localhost:7233 / namespace 'default'); override via env for a hosted server.

Env:
  MODE                 dynamic | static        (default dynamic; overridden by the 1st CLI arg)
  WORKFLOW_NAME        static registration name (default Demo == the served WorkflowSchema.name)
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
from demo_workflow import DemoWorkflow, DemoWorkflowDynamic, WORKFLOW_NAME

TARGET = os.environ.get("TEMPORAL_TARGET", "localhost:7233")
TASK_QUEUE = os.environ.get("TASK_QUEUE", "DEMO_WORKFLOW_QUEUE")


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

    # dynamic -> serve every WorkflowType; static -> serve only WorkflowType == WORKFLOW_NAME
    if mode == "static":
        workflows = [DemoWorkflow]
        served = f"static: only WorkflowType '{WORKFLOW_NAME}'"
    else:
        workflows = [DemoWorkflowDynamic]
        served = "dynamic: any WorkflowType"

    worker = Worker(
        client,
        task_queue=TASK_QUEUE,
        workflows=workflows,
        activities=[demo_start, demo_work, demo_report, demo_human],
    )
    logging.info("worker polling '%s' @ %s (ns=%s, tls=%s) [%s]", TASK_QUEUE, TARGET, namespace, tls, served)
    await worker.run()


if __name__ == "__main__":
    asyncio.run(main())
