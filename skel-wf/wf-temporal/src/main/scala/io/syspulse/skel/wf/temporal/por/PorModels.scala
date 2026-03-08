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
  config: Map[String, String] = Map.empty,
  input: Option[I] = None
)

// Workflow Input - defines all steps (merged from PorRunConfig)

case class PorWorkflowInput(
  poo: StepDef[PooInput] = StepDef(),
  por: StepDef[PorInput] = StepDef(),
  pol: StepDef[PolInput] = StepDef(),
  solvency: StepDef[Unit] = StepDef(),
  report: StepDef[Unit] = StepDef(),
  commit: StepDef[Unit] = StepDef()
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
  fileLink: String,
  waitForConfirmation: Boolean = true,
  /** User signal mode: "file" (poll /tmp), "rest" (POST), "simulate" (delay, default). */
  signalMode: String = "simulate"
)

case class PolFileData(
  ts: Long,
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
  ownerName: String,
  ts: Long,
  tags: Seq[String] = Seq.empty,
  memo: Map[String, String] = Map.empty,
  input: PorWorkflowInput,
  output: PorWorkflowOutput = PorWorkflowOutput()
)
