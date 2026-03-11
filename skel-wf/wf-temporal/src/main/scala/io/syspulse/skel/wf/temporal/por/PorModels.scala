package io.syspulse.skel.wf.temporal.por

import java.util.UUID

// Data Models

case class Wallet(
  address: String,
  network: String,
  balance: BigInt
)

case class WalletWithAsset(
  address: String,
  network: String,
  asset: String,
  balance: BigInt
)

case class Liability(
  userId: UUID,
  asset: String,
  balance: BigInt
)

// Step Definition - contains config and input data for each step

case class StepDef[I](
  config: Map[String, Any] = Map.empty,
  input: Option[I] = None
)

// Workflow Input - defines all steps (merged from PorRunConfig)
// If a step is None, it will be skipped

case class PorWorkflowInput(
  poo: Option[StepDef[PooInput]] = None,
  por: Option[StepDef[PorInput]] = None,
  pol: Option[StepDef[PolInput]] = None,
  solvency: Option[StepDef[Unit]] = None,
  report: Option[StepDef[Unit]] = None,
  commit: Option[StepDef[Unit]] = None
)

case class PooInput(
  wallets: List[Wallet],
  proofType: String // "signature" or "transaction_hash"
)

case class PorInput(
  wallets: List[Wallet],
  assets: List[String] // e.g. ["ETH", "BTC", "LINK", "AAVE", "SOL", "TRX"]
)

case class PolInput(
  fileLink: Option[String] = None,
  waitForConfirmation: Boolean = true,
  config: Map[String, Any] = Map.empty,
  data: Option[PolFileData] = None  // Data in raw format. If file is present it is populated from the file
)

case class PolFileData(
  timestamp: Long,
  liabilities: List[Liability],
  signature: String,
  signatureType: String, // "certificate" or "public_key"
  publicKey: String
)

// Step Outputs

case class PooOutput(
  ts: Long,
  proofs: Map[String, String] // wallet address -> signature or transaction_hash
)

case class PorOutput(
  ts: Long,
  balances: List[WalletWithAsset]
)

case class PolOutput(
  ts: Long,
  liabilities: List[Liability],
  signature: String,
  signatureType: String,
  publicKey: String
)

case class SolvencyOutput(
  porTotalUsd: BigDecimal,
  polTotalUsd: BigDecimal,
  solvencyRatio: BigDecimal // PoL / PoR
)

case class ReportOutput(
  reportFilePath: String,
  reportLink: String
)

case class CommitOutput(
  filePath: String
)

// Workflow Output - contains all step outputs

case class PorWorkflowOutput(
  poo: Option[PooOutput] = None,
  por: Option[PorOutput] = None,
  pol: Option[PolOutput] = None,
  solvency: Option[SolvencyOutput] = None,
  report: Option[ReportOutput] = None,
  commit: Option[CommitOutput] = None
)

// Workflow Run - context propagated through all steps (becomes workflow output)

case class PorWorkflowRun(
  wid: Option[String] = None,   // Workflow ID
  rid: Option[String] = None,   // Run ID

  tid: Option[Int] = None,     // Tenant ID
  pid: Option[Int] = None,     // Project ID  
  proj: Option[String] = None,    // Project (Exchange-1)
  sys: Option[String] = None,     // System (PoR-Audit)

  ts0: Long = System.currentTimeMillis(),
  ts1: Long = 0L,
  tags: Seq[String] = Seq.empty,
  memo: Map[String, String] = Map.empty,
  input: PorWorkflowInput,
  output: PorWorkflowOutput = PorWorkflowOutput()
)
