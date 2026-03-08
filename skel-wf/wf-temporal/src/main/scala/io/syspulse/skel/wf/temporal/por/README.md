# Proof of Reserves (PoR) Workflow Application

This application implements Proof of Reserves workflows using Temporal.

## Overview

The PoR application provides a comprehensive workflow system for verifying cryptocurrency exchange reserves, liabilities, and solvency through multiple configurable flow patterns.

## Components

### Data Models (`PorModels.scala`)
- **Wallet**: Represents a blockchain wallet with address, network, and balance
- **WalletWithAsset**: Wallet balance for a specific asset
- **Liability**: User liability with asset and balance
- **Input/Output models**: For all workflow steps

### Activities (`PorActivities.scala`, `PorActivitiesImpl.scala`)
1. **Proof of Ownership (PoO)**: Generates ownership proofs (signatures or transaction hashes)
2. **Proof of Reserves (PoR)**: Calculates wallet balances across multiple assets
3. **Proof of Liabilities (PoL)**: Human interaction step to load liabilities data
4. **Solvency**: Calculates solvency ratio (Liabilities / Reserves)
5. **Report**: Generates comprehensive markdown report

### Workflows (`PorWorkflow.scala`, `PorWorkflowImpl.scala`)
Supports 4 flow patterns:
- **Flow-1**: PoO → PoR → PoL → Solvency → Report (Complete audit)
- **Flow-2**: PoR → PoL → Solvency → Report (Standard audit)
- **Flow-3**: PoR → Report (Reserves only)
- **Flow-4**: PoO → PoR → Report (Ownership verification)

### Worker & Starter
- **PorWorker**: Temporal worker that processes workflow tasks
- **PorStarter**: CLI tool to execute workflows

## Prerequisites

1. **Temporal Server**: Must be running locally or remotely
   ```bash
   # Start Temporal locally (if using Docker)
   docker run -p 7233:7233 temporalio/auto-setup:latest
   ```

2. **Environment Variables** (optional):
   ```bash
   export TEMPORAL_SERVICE_ADDRESS="127.0.0.1:7233"
   ```

## Usage

### 1. Start the PoR Worker

The worker must be running to process workflow tasks:

```bash
# Using run script
./run-temporal.sh por-worker

# Or directly with sbt
sbt "wf-temporal/runMain io.syspulse.skel.wf.temporal.App por-worker"
```

### 2. Execute PoR Workflows

In a separate terminal, start a workflow:

```bash
# Flow 1: Complete audit (PoO → PoR → PoL → Solvency → Report)
./run-temporal.sh por-start flow-1 Binance

# Flow 2: Standard audit (PoR → PoL → Solvency → Report)
./run-temporal.sh por-start flow-2 Coinbase

# Flow 3: Reserves only (PoR → Report)
./run-temporal.sh por-start flow-3 Kraken

# Flow 4: Ownership verification (PoO → PoR → Report)
./run-temporal.sh por-start flow-4 Gemini
```

Or directly with sbt:

```bash
sbt "wf-temporal/runMain io.syspulse.skel.wf.temporal.App por-start flow-1 MyExchange"
```

## Workflow Details

### Flow 1: Complete Audit
```
[PoO] → [PoR] → [PoL] → [Solvency] → [Report]
```
- Verifies wallet ownership
- Calculates reserves
- Loads liabilities (human interaction)
- Calculates solvency ratio
- Generates comprehensive report

**Event Prefixes**: `proof_of_ownership`, `proof_of_reserves`, `proof_of_liability`, `solvency`, `report`

### Flow 2: Standard Audit
```
[PoR] → [PoL] → [Solvency] → [Report]
```
- Skips ownership verification
- Focuses on reserves vs liabilities analysis

### Flow 3: Reserves Only
```
[PoR] → [Report]
```
- Quick reserves snapshot
- No liability or solvency calculation

### Flow 4: Ownership Verification
```
[PoO] → [PoR] → [Report]
```
- Verifies ownership and reserves
- No liability analysis

## Step Details

### Proof of Ownership (PoO)
**Inputs**:
- List of wallets (address, network, balance)
- Proof type: `signature` or `transaction_hash`

**Outputs**:
- Timestamp
- Map of wallet addresses to proofs

**Execution**: Simulates 1-3 seconds work

### Proof of Reserves (PoR)
**Inputs**:
- List of wallets
- List of assets (BTC, ETH, LINK, AAVE, SOL, TRX)

**Outputs**:
- Timestamp
- List of wallet balances per asset

**Execution**: Simulates 1-3 seconds work

### Proof of Liabilities (PoL)
**Inputs**:
- File link (JSON file path)
- Wait for confirmation flag

**Outputs**:
- Timestamp
- List of liabilities
- Signature and public key

**Execution**:
- Generates demo file in `/tmp/`
- Displays "Timer is waiting for human input"
- Simulates user confirmation (2-4 seconds)

**Demo File Format**:
```json
{
  "timestamp": 1234567890,
  "liabilities": [
    {"userId": "uuid", "asset": "BTC", "balance": "1000000000000000000"}
  ],
  "signature": "0x...",
  "signatureType": "public_key",
  "publicKey": "0x..."
}
```

### Solvency
**Inputs**:
- PoR outputs
- PoL outputs

**Outputs**:
- Total reserves in USD
- Total liabilities in USD
- Solvency ratio (PoL / PoR)

**Execution**: Simulates 1-2 seconds work

### Report
**Inputs**:
- All previous step inputs and outputs

**Outputs**:
- Report file path (in `/tmp/`)
- Report link

**Execution**: Generates markdown report, simulates 1-3 seconds work

## Output

### Generated Files

1. **Liabilities JSON** (during PoL step):
   - Location: `/tmp/liabilities_<timestamp>.json`
   - Contains demo liability data

2. **PoR Report** (final step):
   - Location: `/tmp/por_report_<timestamp>.md`
   - Comprehensive markdown report with:
     - Owner information
     - Proof of Ownership summary (if applicable)
     - Reserves by asset
     - Liabilities by asset (if applicable)
     - Solvency analysis (if applicable)

### Example Report Output

```markdown
# Proof of Reserves Report

## Owner Information
- **Owner Name**: Binance
- **Timestamp**: 1234567890
- **Date**: 2024-03-07

## Proof of Ownership
- **Timestamp**: 1234567891
- **Proofs Count**: 5

## Proof of Reserves
- **Timestamp**: 1234567892
- **Balance Entries**: 30

### Asset Summary
- **BTC**: 100.5
- **ETH**: 50000.0
...

## Solvency Analysis
- **Total Reserves (USD)**: $250,000,000
- **Total Liabilities (USD)**: $240,000,000
- **Solvency Ratio**: 0.96

### Status: ✅ SOLVENT
```

## Monitoring

View workflow execution in Temporal Web UI:
```
http://localhost:8080
```

## Supported Networks

- **Ethereum**: ERC-20 tokens and ETH
- **Bitcoin**: Native BTC
- **Arbitrum**: Layer 2 tokens
- **Solana**: SPL tokens and SOL
- **Tron**: TRC-20 tokens and TRX

## Supported Assets

- BTC (Bitcoin)
- ETH (Ethereum)
- LINK (Chainlink)
- AAVE (Aave)
- SOL (Solana)
- TRX (Tron)

## Error Handling

All activities include proper error handling and logging. Failed steps will cause workflow retry according to Temporal retry policies.

## Event Naming Convention

All workflow events are prefixed with step names:
- `proof_of_ownership.*`
- `proof_of_reserves.*`
- `proof_of_liability.*`
- `solvency.*`
- `report.*`

## Architecture

The application follows Temporal best practices:
- **Activities**: Independent, idempotent operations
- **Workflows**: Orchestrate activities in different patterns
- **Worker**: Polls task queue and executes workflows/activities
- **Starter**: Client to initiate workflow executions

## Future Enhancements

- Real blockchain integration for wallet balance queries
- Actual cryptographic signature verification
- Database integration for liabilities
- Real-time price feeds for USD conversion
- Multi-chain support expansion
- Historical audit trail
- Notification system for solvency alerts
