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

// Step Inputs

case class PorWorkflowInput(
  cexName: String,
  timestamp: Long,
  pooRequired: Boolean,
  porRequired: Boolean,
  polRequired: Boolean,
  reportRequired: Boolean
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
  waitForConfirmation: Boolean = true
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
  timestamp: Long,
  proofs: Map[String, String] // wallet address -> signature or transaction_hash
)

case class PorOutput(
  timestamp: Long,
  balances: List[WalletWithAsset]
)

case class PolOutput(
  timestamp: Long,
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
