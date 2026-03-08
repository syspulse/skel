# PoR (Proof of Reserves) Quick Start Guide

This guide will help you quickly get started with the PoR Workflow application.

## Prerequisites

### 1. Start Temporal Server

If you don't have Temporal running, start it with Docker:

```bash
docker run -p 7233:7233 -p 8080:8080 temporalio/auto-setup:latest
```

Or if you already have it configured, just ensure it's running at `127.0.0.1:7233`

### 2. Verify Temporal is Running

Open your browser to check the Temporal Web UI:
```
http://localhost:8080
```

## Running PoR Workflows

### Step 1: Start the Worker

In terminal 1, start the PoR worker:

```bash
cd /home/andreyk/prj/syspulse/skel/skel/skel-wf/wf-temporal
./run-por-worker.sh
```

You should see:
```
PoR Worker started and listening on task queue: por-task-queue
Press Ctrl+C to stop...
```

### Step 2: Execute a Workflow

In terminal 2, execute a workflow:

#### Flow 1: Complete Audit (Recommended for first test)
```bash
./run-por-flow.sh flow-1 TestExchange
```

This will execute:
1. **Proof of Ownership** - Generate wallet ownership proofs
2. **Proof of Reserves** - Calculate wallet balances
3. **Proof of Liabilities** - Load liabilities data (simulated)
4. **Solvency** - Calculate solvency ratio
5. **Report** - Generate markdown report

#### Other Flows

```bash
# Flow 2: Standard audit (no ownership verification)
./run-por-flow.sh flow-2 Binance

# Flow 3: Reserves only (quick check)
./run-por-flow.sh flow-3 Coinbase

# Flow 4: Ownership + Reserves (no liabilities)
./run-por-flow.sh flow-4 Kraken
```

## Expected Output

### Worker Terminal
You'll see activity logs like:
```
[proof_of_ownership] Starting PoO with 5 wallets, proof type: signature
[proof_of_ownership] Completed PoO with 5 proofs
[proof_of_reserves] Starting PoR with 5 wallets and 6 assets
[proof_of_reserves] Completed PoR with 30 balance entries
[proof_of_liability] Starting PoL - waiting for human input
[proof_of_liability] Timer is waiting for human input
[proof_of_liability] Demo file generated: /tmp/liabilities_1234567890.json
[proof_of_liability] Completed PoL with 10 liability entries
[solvency] Starting Solvency calculation
[solvency] Completed Solvency: Reserves=$250000000, Liabilities=$240000000, Ratio=0.96
[report] Starting Report generation
[report] Completed Report generation: /tmp/por_report_1234567890.md
```

### Starter Terminal
You'll see:
```
Starting PoR Workflow:
  Workflow ID: por-workflow-TestExchange-1234567890
  Owner Name: TestExchange
  Flow: flow-1
  ...

Workflow completed successfully!
Report generated: /tmp/por_report_1234567890.md
Report link: file:///tmp/por_report_1234567890.md
```

### Generated Files

Check the generated report:
```bash
# List recent PoR reports
ls -ltr /tmp/por_report_*.md | tail -5

# View the latest report
cat $(ls -t /tmp/por_report_*.md | head -1)
```

Or open it in your editor:
```bash
code $(ls -t /tmp/por_report_*.md | head -1)
```

## Monitoring in Temporal UI

1. Open http://localhost:8080
2. Click on "Workflows"
3. Find your workflow (search for `por-workflow-TestExchange-`)
4. Click to see execution history with all activity events

You'll see events prefixed with:
- `proof_of_ownership`
- `proof_of_reserves`
- `proof_of_liability`
- `solvency`
- `report`

## Understanding the Flows

### Flow 1: Complete Audit
```
PoO → PoR → PoL → Solvency → Report
```
**Use when**: Full compliance audit needed
**Outputs**: Complete ownership, reserves, liabilities, and solvency analysis

### Flow 2: Standard Audit
```
PoR → PoL → Solvency → Report
```
**Use when**: Regular solvency check (ownership already verified)
**Outputs**: Reserves, liabilities, and solvency ratio

### Flow 3: Reserves Only
```
PoR → Report
```
**Use when**: Quick reserves snapshot
**Outputs**: Wallet balances only

### Flow 4: Ownership Verification
```
PoO → PoR → Report
```
**Use when**: Verify ownership and reserves without liability analysis
**Outputs**: Ownership proofs and reserves

## Troubleshooting

### Worker not starting?
- Ensure Temporal is running: `curl http://localhost:8080`
- Check TEMPORAL_SERVICE_ADDRESS environment variable

### Workflow not executing?
- Ensure worker is running first
- Check Temporal UI for error messages
- Verify namespace (default is used)

### Can't find reports?
```bash
ls -ltr /tmp/por_report_*.md
ls -ltr /tmp/liabilities_*.json
```

## Next Steps

1. **Explore the code**: See `src/main/scala/io/syspulse/skel/wf/temporal/por/`
2. **Read detailed docs**: See `src/main/scala/io/syspulse/skel/wf/temporal/por/README.md`
3. **Customize workflows**: Modify `PorWorkflowImpl.scala`
4. **Add real integrations**: Update `PorActivitiesImpl.scala`

## Clean Up

Stop the worker:
```bash
# In the worker terminal, press Ctrl+C
```

Remove test files:
```bash
rm /tmp/por_report_*.md
rm /tmp/liabilities_*.json
```

## Summary

✅ You've successfully:
1. Started a Temporal worker for PoR workflows
2. Executed a Proof of Reserves workflow
3. Generated a comprehensive audit report
4. Learned about the 4 different flow patterns

For more details, see the full README in the por directory.
