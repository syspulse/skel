"""
Demo activities: DemoStart -> DemoWork -> DemoReport.

Each activity uses the WorkflowConfig.id (`cid`) - which wf-ext passed as the Temporal *Memo* on
start - to read its WorkflowConfig from the wf-ext REST API (GET /config/{cid}). This is how a worker,
which knows nothing about wf-ext's store, pulls its own configuration at runtime.

Every activity prints a START line (preceded by a blank line) and an END line.

Activities run OUTSIDE the workflow sandbox, so network I/O (httpx) and asyncio.sleep are allowed here.
"""
import asyncio
import json
import os

import httpx
from temporalio import activity

# Base URL of the wf-ext WorkflowConfig API (…/config/{id}) and an optional bearer token.
WF_EXT_URL = os.environ.get("WF_EXT_URL", "http://localhost:8080/api/v1/wf/ext").rstrip("/")
WF_EXT_TOKEN = os.environ.get("WF_EXT_TOKEN", "").strip()


def _start(name: str, cid: int) -> None:
    print(f"\n[{name}] START cid={cid}", flush=True)


def _end(name: str, cid: int, extra: str = "") -> None:
    print(f"[{name}] END cid={cid}{(' ' + extra) if extra else ''}", flush=True)


async def _get(path: str) -> dict:
    headers = {"Accept": "application/json"}
    if WF_EXT_TOKEN:
        headers["Authorization"] = f"Bearer {WF_EXT_TOKEN}"
    async with httpx.AsyncClient(timeout=15.0) as client:
        resp = await client.get(f"{WF_EXT_URL}{path}", headers=headers)
        resp.raise_for_status()
        return resp.json()


async def fetch_config_view(cid: int, entity: str = "") -> dict:
    """GET the WorkflowConfigView for `cid` (optionally with ?entity=detector to include detectors)."""
    q = f"?entity={entity}" if entity else ""
    return await _get(f"/config/{cid}{q}")   # { "config": {...}, "detectors": {cid->DetectorConfig}, ... }


async def fetch_config(cid: int) -> dict:
    """The WorkflowConfig object of `cid`."""
    return (await fetch_config_view(cid)).get("config", {})


async def fetch_detector_config(detector_id: int) -> dict:
    """GET a single DetectorConfig by its id (fresh - it may have changed since the workflow started)."""
    return await _get(f"/detector/config/{detector_id}")


def _input_of(cfg: dict) -> dict:
    """Parse the start-input JSON that wf-ext stored in config.meta.input into a dict ({} if none/bad)."""
    raw = (cfg.get("meta") or {}).get("input")
    if isinstance(raw, str) and raw.strip():
        try:
            parsed = json.loads(raw)
            return parsed if isinstance(parsed, dict) else {}
        except json.JSONDecodeError:
            return {}
    return {}


@activity.defn(name="DemoStart")
async def demo_start(cid: int) -> dict:
    _start("DemoStart", cid)
    # read the config WITH its detectors (?entity=detector) so the workflow learns its topology here
    view = await fetch_config_view(cid, entity="detector")
    cfg = view.get("config", {})
    # detectors map is keyed by DetectorConfig id -> DetectorConfig
    detectors = [{"id": d.get("id"), "name": d.get("name"), "status": d.get("status")}
                 for d in (view.get("detectors") or {}).values()]
    activity.logger.info(
        "DemoStart cid=%s name=%s title=%s status=%s detectors=%s", cid,
        cfg.get("name"), cfg.get("title"), cfg.get("status"), detectors,
    )
    result = {
        "activity": "DemoStart",
        "cid": cid,
        "name": cfg.get("name"),
        "title": cfg.get("title"),
        "status": cfg.get("status"),
        "detectors": detectors,
    }
    _end("DemoStart", cid)
    return result


@activity.defn(name="DemoWork")
async def demo_work(cid: int) -> dict:
    _start("DemoWork", cid)
    cfg = await fetch_config(cid)
    wf_input = _input_of(cfg)
    # sleep for the input's "n" seconds (0 if absent/invalid)
    try:
        n = int(wf_input.get("n", 0))
    except (ValueError, TypeError):
        n = 0
    n = max(0, n)
    if n:
        activity.logger.info("DemoWork cid=%s sleeping n=%s seconds", cid, n)
        await asyncio.sleep(n)
    result = {
        "activity": "DemoWork",
        "cid": cid,
        "input": wf_input,
        "slept": n,
    }
    _end("DemoWork", cid, f"(slept {n}s)")
    return result


@activity.defn(name="DemoReport")
async def demo_report(cid: int, work: dict) -> dict:
    _start("DemoReport", cid)
    cfg = await fetch_config(cid)
    report = {
        "activity": "DemoReport",
        "cid": cid,
        "workflow": cfg.get("name"),
        "title": cfg.get("title"),
        "work": work,
        "ok": True,
    }
    activity.logger.info("DemoReport cid=%s report=%s", cid, report)
    _end("DemoReport", cid)
    return report


@activity.defn(name="DemoHuman")
async def demo_human(cid: int, detector_id: int, poll_sec: int = 3) -> dict:
    """Human-gated step, LONG-RUNNING so it is visible in the engine (mapped to the DemoHuman
    DetectorConfig) while it waits. The wait loop ONLY heartbeats - it does NOT read the config. The
    workflow RELEASES it (cancels it) when the CONTINUE signal arrives; ONLY THEN does it re-read the
    DetectorConfig fresh (it may have been edited while waiting) and print it."""
    _start("DemoHuman", cid)
    poll = 0
    # WAIT (visible + heartbeating) until released by the CONTINUE signal - no config read in here.
    try:
        while True:
            poll += 1
            activity.logger.info("DemoHuman WAITING cid=%s poll=%s (detector id=%s) for CONTINUE ...", cid, poll, detector_id)
            activity.heartbeat(poll)  # keep the activity alive + let cancellation be delivered
            await asyncio.sleep(max(1, poll_sec))
    except asyncio.CancelledError:
        pass
    # released by the CONTINUE signal -> NOW (once, after the signal) re-read the DetectorConfig fresh
    # and print it. shield the read so the in-flight cancellation doesn't abort it.
    dc = await asyncio.shield(fetch_detector_config(detector_id))
    activity.logger.info(
        "DemoHuman RELEASED cid=%s (CONTINUE) detector id=%s name=%s status=%s config=%s",
        cid, dc.get("id"), dc.get("name"), dc.get("status"), dc.get("config"),
    )
    _end("DemoHuman", cid, "(released by CONTINUE)")
    return {
        "activity": "DemoHuman",
        "cid": cid,
        "released": True,
        "polls": poll,
        "detector": {"id": dc.get("id"), "name": dc.get("name"), "status": dc.get("status"), "config": dc.get("config")},
    }
