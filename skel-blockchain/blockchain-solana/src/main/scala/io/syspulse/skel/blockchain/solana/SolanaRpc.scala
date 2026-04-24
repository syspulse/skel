package io.syspulse.skel.blockchain.solana

import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}
import scala.concurrent.{Future, ExecutionContext, Await}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import io.syspulse.skel.blockchain.Blockchain

import spray.json._
import spray.json.DefaultJsonProtocol._

import io.syspulse.skel.HTTP

/** `info` for transfer-style instructions (system transfer, etc.). */

// SPL-Token Transfer Info
// "parsed": {
//   "info": {
//     "destination": "9gD46MnYLpskDiRisMZGY958JHgMSxLUiT56r8vdNzb8",
//     "mint": "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
//     "multisigAuthority": "41zCUJsKk6cMB94DDtm99qWmyMZfp4GkAhhuz4xTwePu",
//     "signers": [
//       "41zCUJsKk6cMB94DDtm99qWmyMZfp4GkAhhuz4xTwePu"
//     ],
//     "source": "CVJvFmYcpqq3u7i5cqDK6dqDkTx14Lg8NwUgsLBswcLu",
//     "tokenAmount": {
//       "amount": "22440947258",
//       "decimals": 6,
//       "uiAmount": 22440.947258,
//       "uiAmountString": "22440.947258"
//     }
//   },
//   "type": "transferChecked"
// },

case class TokenAmount(
  amount: String,
  decimals: Int,
  uiAmount: Double,
  uiAmountString: String
)

case class SolTransferInfo(
  source: String,
  destination: String,
  lamports: Option[Long],
  
  tokenAmount: Option[TokenAmount],
  multisigAuthority: Option[String],
  signers: Option[Seq[String]],
  mint: Option[String],

  amount: Option[String],
  authority: Option[String],
)

object SolTransferInfo {  
  implicit val jf_token_amount: RootJsonFormat[TokenAmount] = jsonFormat4(TokenAmount)
  implicit val jf_sol_tr_info: RootJsonFormat[SolTransferInfo] = jsonFormat9(SolTransferInfo)
}

case class SolTransfer(
  from: String, // address (token account for SPL transfers; wallet for native SOL)
  to: String,   // address (token account for SPL transfers; wallet for native SOL)
  value: Long,  // raw amount (token base units or lamports)
  programId: String, // program id (Tokenkeg.., SystemProgramId, ...)
  program: String,   // token address for SPL transfers; NativeSolMint for native SOL
  fromOwner: Option[String] = None, // wallet owner for SPL token accounts (when known)
  toOwner: Option[String] = None,   // wallet owner for SPL token accounts (when known)
  fromTokenAccount: Option[String] = None, // SPL token account address (same as `from`)
  toTokenAccount: Option[String] = None,   // SPL token account address (same as `to`)
)

/**
 * Post-transaction balance snapshot from RPC meta (`bal1` = post lamports per account,
 * `tok0` = post token balances).
 *
 * Two row kinds are concatenated:
 * - **Native**: one row per account in `tx.acc` with `mint == None` and `lamports` from `bal1`.
 * - **SPL**: one row per entry in `tok0` with token fields set; `lamports` is the same account's
 *   post SOL balance (rent + …) at that index when `bal1` is present.
 */
case class SolBalance(
  accountIndex: Int,
  address: String,
  lamports: Long,
  mint: Option[String] = None,
  owner: Option[String] = None,
  programId: Option[String] = None,
  raw: Option[BigInt] = None,
  decimals: Option[Int] = None,
)

/**
 * One top-level instruction: account addresses passed to the instruction (`from`) and the
 * program id being invoked (`to`).
 */
case class InstructionCall(
  from: Seq[String],
  to: String,
)

/**
 * Normalized top-level RPC instruction — closest analogue to an EVM “call” (selector + calldata + address):
 * account metas, opaque instruction `data`, optional parsed `type` / `program` labels, and invoked `programId`.
 *
 * @param `type` Parsed instruction name when RPC returned `parsed` (e.g. `transferChecked`); empty if unparsed.
 * @param program Optional human-readable program label from RPC when present (e.g. `spl-token`).
 */
case class SolCall(
  accounts: Seq[String],
  data: Option[String],
  `type`: String,
  program: Option[String],
  programId: String,
)

/**
 * Program-related data: callees, which also appear as account keys, and per-instruction call shape.
 */
case class DecodedPrograms(
  /** Distinct programs invoked by top-level instructions (`programId`), first-seen order. */
  invoked: Seq[String],
  /**
   * Subset of `invoked` also present in `tx.acc` or instruction `accounts`
   * (program as account key, not only callee field).
   */
  asKeys: Seq[String],
  /** Per top-level instruction: instruction `accounts` → `programId`. */
  calls: Seq[InstructionCall],
)

/**
 * Result of [[Solana.decodeAddresses]]:
 *   - `from` — distinct non-program addresses (message keys + instruction accounts, excluding any invoked program id).
 *   - `programs` — invoked programs, key overlap, and [[InstructionCall]] list.
 */
case class DecodedAddresses(
  accounts: Seq[String],
  programs: DecodedPrograms,
)

case class SolanaBalance(value: BigInt, dec: Option[Int] = None)

final class SolanaRpc( val rpcUrl: String, val timeout: FiniteDuration = FiniteDuration(10000L, TimeUnit.MILLISECONDS))
  (implicit ec: ExecutionContext = ExecutionContext.global) {

  val log = Logger(s"${this}")

  private val id = new AtomicLong(1L)
  private val headers = Seq("Content-Type" -> "application/json")

  private val maxBlocks: Int = 100

  // Cache is per block (slot). Each block keeps (addr,token) -> balance
  // Uses insertion order for eviction (oldest blocks removed first).
  private val cacheByBlock = mutable.LinkedHashMap.empty[Long, mutable.Map[(String, String), SolanaBalance]]

  private def blockKey(block: Option[Long]): Long =
    block.getOrElse(0L)

  private def ensureBlock(block: Long): mutable.Map[(String, String), SolanaBalance] = {
    cacheByBlock.getOrElseUpdate(block, mutable.Map.empty[(String, String), SolanaBalance])
  }

  private def evictIfNeeded() = {
    while (cacheByBlock.size > maxBlocks) {
      cacheByBlock.headOption.foreach { case (b, _) => cacheByBlock.remove(b) }
    }
  }

  private def findBalance(addr: String, token: String, block: Option[Long]): Option[SolanaBalance] =
    cacheByBlock.get(blockKey(block)).flatMap(_.get((addr, token)))

  private def addBalance(addr: String, token: String, block: Option[Long], balance: SolanaBalance): Unit = {
    val b = blockKey(block)
    ensureBlock(b).put((addr, token), balance)
    evictIfNeeded()
  }

  def sync[A](fa: => Future[A], t: FiniteDuration = timeout): Try[A] =
    Try(Await.result(fa, t))

  def callAsync(method: String, params: JsValue): Future[JsValue] = {
    val request = JsObject(
      "jsonrpc" -> JsString("2.0"),
      "id" -> JsNumber(id.getAndIncrement()),
      "method" -> JsString(method),
      "params" -> params
    )

    log.debug(s"${method} -> ${rpcUrl}: '${request.compactPrint}'")

    HTTP
      .post(
        rpcUrl,
        Some(request.compactPrint),        
        headers,
        timeout.toMillis
      )
      .map { response =>
        log.debug(s"${method} <- ${rpcUrl}: '${response}'")
        val json = response.parseJson.asJsObject
        json.fields.get("result") match {
          case Some(result) => result
          case None =>
            json.fields.get("error") match {
              case Some(error) => throw new Exception(s"RPC error: ${error}")
              case None => throw new Exception("No result or error in response")
            }
        }
      }
  }

  def call(method: String, params: JsValue): Future[JsValue] = callAsync(method, params)
  

  private def getBalanceRpc(address: String): Future[SolanaBalance] = {
    val params = JsArray(JsString(address))
    call("getBalance", params).map { result =>
      val value = result.asJsObject.fields("value").convertTo[Long]
      SolanaBalance(BigInt(value), Some(9)) // SOL has 9 decimals
    }
  }

  def getBalance(address: String, block: Option[Long] = None, cached: Boolean = true): Future[SolanaBalance] =
    if (!cached) {
      getBalanceRpc(address)
    } else {
      findBalance(address, "SOL", block) match {
        case Some(b) => Future.successful(b)
        case None =>
          getBalanceRpc(address).map { b =>
            addBalance(address, "SOL", block, b)
            b
          }
      }
    }

  def getTokenAccountsByOwner(ownerAddress: String, mint: String): Future[Seq[String]] = {
    val params = JsArray(
      JsString(ownerAddress),
      JsObject("mint" -> JsString(mint)),
      JsObject("encoding" -> JsString("jsonParsed"))
    )

    call("getTokenAccountsByOwner", params)
      .map(_.asJsObject.fields("value").convertTo[Seq[JsValue]])
      .map(_.map(_.asJsObject.fields("pubkey").convertTo[String]))
  }

  private def parseTokenBalance(result: JsValue): Option[SolanaBalance] = {
    // Result format:
    // {
    //   "value":[
    //     {"pubkey":"...","account":{"data":{"parsed":{"info":{"tokenAmount":{"amount":"123","decimals":6}}}}}}
    //   ]
    // }
    try {
      val accounts = result.asJsObject.fields("value").convertTo[Seq[JsValue]]
      if (accounts.isEmpty) return Some(SolanaBalance(BigInt(0)))

      val amounts: Seq[(BigInt, Int)] = accounts.flatMap { acc =>
        val info =
          acc.asJsObject.fields("account").asJsObject
            .fields("data").asJsObject
            .fields("parsed").asJsObject
            .fields("info").asJsObject
            .fields("tokenAmount").asJsObject

        val amountStr = info.fields("amount").convertTo[String]
        val decimals = info.fields("decimals").convertTo[Int]
        Some((BigInt(amountStr), decimals))
      }

      if (amounts.isEmpty) Some(SolanaBalance(BigInt(0)))
      else {
        // Decimals should be identical for a given mint; pick the first and sum all amounts.
        val dec0 = amounts.head._2
        val sum = amounts.map(_._1).sum
        Some(SolanaBalance(sum, Some(dec0)))
      }
    } catch {
      case _: Throwable => None
    }
  }

  private def getTokenBalanceRpc(ownerAddress: String, mint: String): Future[SolanaBalance] = {
    val params = JsArray(
      JsString(ownerAddress),
      JsObject("mint" -> JsString(mint)),
      JsObject("encoding" -> JsString("jsonParsed"))
    )

    // Parse balances directly from getTokenAccountsByOwner (no extra RPC).
    call("getTokenAccountsByOwner", params)
      .map(res => parseTokenBalance(res).getOrElse(SolanaBalance(BigInt(0))))
  }

  def getTokenBalance(ownerAddress: String, mint: String, block: Option[Long] = None, cached: Boolean = true): Future[SolanaBalance] =
    if (!cached) {
      getTokenBalanceRpc(ownerAddress, mint)
    } else {
      findBalance(ownerAddress, mint, block) match {
        case Some(b) => Future.successful(b)
        case None =>
          getTokenBalanceRpc(ownerAddress, mint).map { b =>
            addBalance(ownerAddress, mint, block, b)
            b
          }
      }
    }

  /** Fetch balances for many token mints for the same owner. */
  def getTokenBalances( ownerAddress: String, mints: Seq[String], block: Option[Long] = None, cached: Boolean = true): Future[Map[String, SolanaBalance]] = {
    val uniq = mints.distinct
    Future
      .traverse(uniq) { mint =>
        getTokenBalance(ownerAddress, mint, block = block, cached = cached).map(b => mint -> b)
      }
      .map(_.toMap)
  }
}
