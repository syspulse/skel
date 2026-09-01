"""
Demo workflow, available in TWO registration modes (the worker picks one):

  - DYNAMIC  (DemoWorkflowDynamic, @workflow.defn(dynamic=True))  - runs for ANY WorkflowType.
  - STATIC   DemoWorkflow ("Demo") + DemoHumanWorkflow ("Demo-Human") - the two demo WorkflowTypes.
             Extra names from WORKFLOW_NAME (CSV) are also registered.

Both modes: wf-ext passes top-level Temporal Memo fields "cid" (WorkflowConfig.id) and "sid"
(WorkflowSchema.id). The workflow reads its topology from the WorkflowConfig (?entity=detector) and
adapts:

  DemoStart -> DemoWork -> [ DemoHuman (only if a "DemoHuman" detector is wired in) ] -> DemoReport

If a "DemoHuman" DetectorConfig is part of the workflow, the workflow WAITS for a "CONTINUE" signal
(human input; send it with wf-config-signal.sh or POST /config/{cid}/signal). On CONTINUE, the
DemoHuman activity RE-READS that DetectorConfig fresh (it may have been edited while waiting) and prints
it. If "DemoHuman" is absent, the workflow runs straight through as before.

The "Demo-Human" WorkflowType is the schema that wires DemoHuman in; "Demo" is the 3-activity path.
Both types share this orchestration (the human step is still gated on the detector being present).
"""
from collections.abc import Sequence  # temporalio requires the dynamic arg typed as collections.abc.Sequence
from datetime import timedelta

from temporalio import workflow
from temporalio.common import RawValue

# `os` + the activity functions are host-side; pass them through the workflow sandbox unchanged.
with workflow.unsafe.imports_passed_through():
    import os
    from activities import demo_start, demo_work, demo_report, demo_human

# Built-in static WorkflowTypes (== WorkflowSchema.name). Both are always registered.
WORKFLOW_NAME_DEMO = "Demo"
WORKFLOW_NAME_HUMAN = "Demo-Human"
# Extra static names (CSV). "Demo" / "Demo-Human" are always included even if omitted here.
WORKFLOW_NAME = os.environ.get("WORKFLOW_NAME", f"{WORKFLOW_NAME_DEMO},{WORKFLOW_NAME_HUMAN}")
# A detector with this name turns the workflow into a human-gated flow (waits for the CONTINUE signal).
HUMAN_DETECTOR = "DemoHuman"
# The signal name the human/external caller sends to release the DemoHuman step.
CONTINUE_SIGNAL = "CONTINUE"


def _static_names() -> list:
    names = [WORKFLOW_NAME_DEMO, WORKFLOW_NAME_HUMAN]
    for n in (s.strip() for s in WORKFLOW_NAME.split(",")):
        if n and n not in names:
            names.append(n)
    return names


WORKFLOW_NAMES = _static_names()


class DemoBase:
    """Shared state + signal handler + orchestration for both the dynamic and static workflows."""

    def __init__(self) -> None:
        self._continue = False
        self._continue_payload = None

    # human/external input: wf-ext POST /config/{cid}/signal?name=CONTINUE -> this handler
    @workflow.signal(name=CONTINUE_SIGNAL)
    def continue_signal(self, payload=None) -> None:
        workflow.logger.info("CONTINUE signal received payload=%s", payload)
        self._continue_payload = payload
        self._continue = True

    async def run_demo(self, cid: int, sid: int, wf_type: str, wf_input) -> dict:
        opts = dict(start_to_close_timeout=timedelta(seconds=30))
        # DemoWork sleeps for the input's "n" seconds, so its timeout must comfortably cover that.
        n = 0
        if isinstance(wf_input, dict):
            try:
                n = max(0, int(wf_input.get("n", 0)))
            except (ValueError, TypeError):
                n = 0
        work_opts = dict(start_to_close_timeout=timedelta(seconds=n + 30))

        # DemoStart returns the topology (configured detectors) so the workflow can adapt
        started = await workflow.execute_activity(demo_start, cid, **opts)
        work = await workflow.execute_activity(demo_work, cid, **work_opts)

        # is a "DemoHuman" detector wired into this workflow? (optional human-gated step)
        detectors = started.get("detectors", []) if isinstance(started, dict) else []
        human = next((d for d in detectors if d.get("name") == HUMAN_DETECTOR), None)

        human_result = None
        if human:
            workflow.logger.info(
                "%s present (detector id=%s) - starting DemoHuman activity, waiting for the '%s' signal ...",
                HUMAN_DETECTOR, human.get("id"), CONTINUE_SIGNAL,
            )
            # start DemoHuman as a long-running activity so it is VISIBLE (mapped to the DetectorConfig)
            # while it waits and re-reads the config; release it (cancel) once the CONTINUE signal arrives.
            human_handle = workflow.start_activity(
                demo_human, args=[cid, human.get("id")],
                start_to_close_timeout=timedelta(hours=1),
                heartbeat_timeout=timedelta(seconds=10),
            )
            await workflow.wait_condition(lambda: self._continue)   # block until human input arrives
            human_handle.cancel()
            try:
                human_result = await human_handle
            except Exception as e:  # noqa: BLE001 - cancellation surfaces as an activity error; the work is done
                workflow.logger.info("DemoHuman released (cancel surfaced as %s)", type(e).__name__)
                human_result = {"activity": "DemoHuman", "released": True}

        report = await workflow.execute_activity(demo_report, args=[cid, work], **opts)
        return {"cid": cid, "sid": sid, "type": wf_type, "input": wf_input, "detectors": detectors,
                "started": started, "work": work, "human": human_result, "report": report}


@workflow.defn(dynamic=True)
class DemoWorkflowDynamic(DemoBase):
    @workflow.run
    async def run(self, args: Sequence[RawValue]) -> dict:
        cid = workflow.memo_value("cid", 0, type_hint=int)
        sid = workflow.memo_value("sid", 0, type_hint=int)
        wf_type = workflow.info().workflow_type
        # wf-ext sends a single JSON payload (the caller input, or the config JSON as a fallback).
        wf_input = workflow.payload_converter().from_payload(args[0].payload) if args else None
        workflow.logger.info("Demo(dynamic) type='%s' cid=%s sid=%s input=%s", wf_type, cid, sid, wf_input)
        return await self.run_demo(cid, sid, wf_type, wf_input)


class _DemoStatic(DemoBase):
    """Shared @workflow.run body for every statically registered demo WorkflowType."""

    async def run_static(self, wf_input=None) -> dict:
        cid = workflow.memo_value("cid", 0, type_hint=int)
        sid = workflow.memo_value("sid", 0, type_hint=int)
        wf_type = workflow.info().workflow_type
        workflow.logger.info("Demo(static) type='%s' cid=%s sid=%s input=%s", wf_type, cid, sid, wf_input)
        return await self.run_demo(cid, sid, wf_type, wf_input)


@workflow.defn(name=WORKFLOW_NAME_DEMO)
class DemoWorkflow(_DemoStatic):
    @workflow.run
    async def run(self, wf_input=None) -> dict:
        return await self.run_static(wf_input)


@workflow.defn(name=WORKFLOW_NAME_HUMAN)
class DemoHumanWorkflow(_DemoStatic):
    """Static registration for WorkflowType 'Demo-Human' (schema that wires the DemoHuman activity)."""

    @workflow.run
    async def run(self, wf_input=None) -> dict:
        return await self.run_static(wf_input)


def _extra_static_workflow(name: str):
    @workflow.defn(name=name)
    class Extra(_DemoStatic):
        @workflow.run
        async def run(self, wf_input=None) -> dict:
            return await self.run_static(wf_input)
    Extra.__name__ = "DemoWorkflow_" + "".join(c if c.isalnum() else "_" for c in name)
    Extra.__qualname__ = Extra.__name__
    return Extra


_BUILTINS = {
    WORKFLOW_NAME_DEMO: DemoWorkflow,
    WORKFLOW_NAME_HUMAN: DemoHumanWorkflow,
}
STATIC_WORKFLOWS = [_BUILTINS.get(n) or _extra_static_workflow(n) for n in WORKFLOW_NAMES]
