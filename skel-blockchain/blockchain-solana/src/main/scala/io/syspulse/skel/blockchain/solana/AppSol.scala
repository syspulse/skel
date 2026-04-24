package io.syspulse.skel.blockchain.solana

import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

import spray.json._

import io.syspulse.skel
import io.syspulse.skel.config._
import io.syspulse.skel.blockchain.BlockchainRpc
import io.syspulse.skel.blockchain.solana.SolanaRpc

case class ConfigSol(
  rpc: String = "https://api.mainnet-beta.solana.com",
  chain: String = "solana",         
  timeout: Long = 10000L,
  cache: Boolean = true,

  block: Option[Long] = None,

  cmd: String = "health",
  params: Seq[String] = Seq.empty,
)

/**
  * Minimal CLI to test SolanaRpc.
  *
  * Examples:
  * - health:
  *   `AppSol --rpc=https://api.mainnet-beta.solana.com health`
  * - balance (native):
  *   `AppSol --rpc=https://api.mainnet-beta.solana.com balance <address> [block] [cached=true|false]`
  * - token balance:
  *   `AppSol --rpc=https://api.mainnet-beta.solana.com token-balance <owner> <mint> <decimals> [block] [cached=true|false]`
  */
object AppSol extends skel.Server {
  
  private def resolveRpcUri(rpcArg: String, chain: String): String = {
    // allow direct URL
    if (rpcArg.startsWith("http://") || rpcArg.startsWith("https://")) return rpcArg

    // otherwise treat as BlockchainRpc config string(s)
    // formats supported by BlockchainRpc.from:
    // - "name=id=rpcUri"
    // - "rpcUri=id"
    // - "rpcUri"
    val bb = BlockchainRpc.from(Seq(rpcArg))
    bb.get(chain)
      .orElse(bb.values.headOption)
      .map(_.rpcUri)
      .getOrElse(rpcArg)
  }

  def main(args: Array[String]): Unit = {
    val d = ConfigSol()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv,
      new ConfigurationArgs(args, "solana", "",
        ArgString('_', "rpc", s"Solana RPC url or BlockchainRpc entry (def: '${d.rpc}')"),
        ArgString('_', "chain", s"BlockchainRpc chain id key (def: '${d.chain}')"),
        ArgLong('_', "timeout", s"RPC timeout msec (def: ${d.timeout})"),
        ArgInt('_', "cache", s"Cache results (def: ${d.cache})"),
        ArgLong('_', "block", s"Block number (def: ${d.block})"),

        ArgCmd("health", "Health check"),
        ArgCmd("balance", "Address native Balance"),
        ArgCmd("token-accounts", "Token accounts for address"),
        ArgCmd("token-balance", "Token balances for address"),

        ArgParam("<params>", ""),
        ArgLogging(),
        ArgConfig(),
        ArgUnknown(),
      ).withExit(1)
    )).withLogging()

    implicit val config: ConfigSol = ConfigSol(
      rpc = c.getString("rpc").getOrElse(d.rpc),
      chain = c.getString("chain").getOrElse(d.chain),
      timeout = c.getLong("timeout").getOrElse(d.timeout),
      cache = c.getInt("cache").map(_ > 0).getOrElse(d.cache),
      block = c.getLong("block"),
      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    val rpcUri = resolveRpcUri(config.rpc, config.chain)
    val rpc = new SolanaRpc(rpcUri, timeout = FiniteDuration(config.timeout, MILLISECONDS))

    val r: Try[Any] = config.cmd match {
      case "health" =>
        rpc.sync(rpc.call("getHealth", JsArray()))

      case "balance" => 
        config.params.toList match {
          case address :: rest =>
            rpc.sync(rpc.getBalance(address, block = config.block, cached = config.cache))
          case _ =>
            Failure(new IllegalArgumentException(
              s"Unknown params: ${config.params.mkString(" ")}"
            ))
        }

      case "token-accounts" =>
        config.params.toList match {
          case owner :: mint :: rest =>
            rpc.sync(rpc.getTokenAccountsByOwner(owner, mint))          
          case _ =>
            Failure(new IllegalArgumentException(
              s"Unknown params: ${config.params.mkString(" ")}"
            ))
        }

      case "token-balance" =>
        config.params.toList match {
          case owner :: mint :: Nil =>            
            rpc.sync(rpc.getTokenBalance(owner, mint, block = config.block, cached = config.cache))
          case owner :: rest =>
            val mints = rest.map(_.split(',')).flatten
            rpc.sync(rpc.getTokenBalances(owner, mints.toSeq))
          case _ =>
            Failure(new IllegalArgumentException(
              s"Unknown params: ${config.params.mkString(" ")}"
            ))
        }

      case _ =>
        Failure(new IllegalArgumentException(
          s"Unknown params: ${config.params.mkString(" ")}"
        ))
    }

    Console.err.println(s"${r}")

  }
}

