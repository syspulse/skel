package io.syspulse.skel.wf.temporal.por.demo

import scala.util.Random
import java.util.UUID
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.net.InetSocketAddress
import com.sun.net.httpserver.{HttpServer, HttpExchange, HttpHandler}
import io.temporal.activity.Activity
import com.typesafe.scalalogging.Logger
import io.syspulse.skel.wf.temporal.por._

class PolActivityDemo {
  private val log = Logger(getClass.getName)
  private val SignalPollIntervalMs = 5000L
  
  /** Wait for user signal: file (poll /tmp), rest (POST to local server), or simulate (delay). Mode from PolInput.signalMode. */
  private def waitForUserSignal(workflowId: String, wid: String, signalMode: String): Unit = {
    signalMode.toLowerCase match {
      case "file" => waitForFileSignal(workflowId, wid)
      case "rest" => waitForRestSignal(wid)
      case _ => waitForSimulateSignal()
    }
  }

  /** Poll /tmp/por-pol-{workflowId}.signal until file exists with non-empty content. */
  private def waitForFileSignal(workflowId: String, wid: String): Unit = {
    val safeWid = workflowId.replaceAll("[^a-zA-Z0-9_.-]", "_")
    val path = os.Path(s"/tmp/por-pol-${safeWid}.signal", os.pwd)
    log.info(s"$wid Waiting for signal file: $path (poll every ${SignalPollIntervalMs}ms)")
    var attempt = 0
    var done = false
    while (!done) {
      attempt += 1
      val content = scala.util.Try(os.read(path)).toOption.flatMap(s => Some(s.trim)).find(_.nonEmpty)
      if (content.isDefined) {
        log.info(s"$wid Signal file received (attempt $attempt)")
        done = true
      } else {
        log.info(s"$wid Poll attempt $attempt: signal file missing or empty, retrying in ${SignalPollIntervalMs}ms")
        Thread.sleep(SignalPollIntervalMs)
      }
    }
  }

  /** Start HTTP server; block until one POST to /pol-signal (body = file content). Port from POL_SIGNAL_PORT or random. */
  private def waitForRestSignal(wid: String): Unit = {
    val port = sys.env.get("POL_SIGNAL_PORT").fold(0)(_.toInt)
    val latch = new CountDownLatch(1)
    @volatile var receivedBody: Option[String] = None
    val server = HttpServer.create(new InetSocketAddress(port), 0)
    server.createContext("/pol-signal", new HttpHandler {
      override def handle(ex: HttpExchange): Unit = {
        if ("POST".equalsIgnoreCase(ex.getRequestMethod)) {
          val body = scala.io.Source.fromInputStream(ex.getRequestBody).mkString
          receivedBody = Some(body)
          ex.sendResponseHeaders(200, 0)
          ex.close()
          latch.countDown()
        } else {
          ex.sendResponseHeaders(405, -1)
          ex.close()
        }
      }
    })
    server.start()
    val actualPort = server.getAddress.getPort
    log.info(s"$wid REST signal: POST http://<host>:${actualPort}/pol-signal with body to continue")
    latch.await(24, TimeUnit.HOURS)
    server.stop(0)
    log.info(s"$wid REST signal received")
  }

  private def waitForSimulateSignal(): Unit = {
    PorActivitiesDemo.simulateWork(2, 4)
  }

  def execute(run: PorWorkflowRun): PorWorkflowRun = {
    val activityInfo = Activity.getExecutionContext.getInfo
    val workflowId = activityInfo.getWorkflowId
    val wid = s"[$workflowId / ${activityInfo.getRunId}]"

    run.input.pol.flatMap(_.input) match {
      case None =>
        log.warn(s"$wid PoL: No input provided, returning run unchanged")
        run

      case Some(input) =>
        log.info(s"$wid Starting PoL - waiting for human input")
        log.info(s"$wid Timer is waiting for human input")

        // Generate demo file
        val demoFilePath = os.temp.dir() / s"liabilities_${System.currentTimeMillis()}.json"
        val demoData = generateDemoLiabilitiesFile()

        // Write demo file
        val jsonContent = s"""{
  "ts": ${demoData.ts},
  "liabilities": [
${demoData.liabilities.map(l => s"""    {"userId": "${l.userId}", "asset": "${l.asset}", "balance": "${l.balance}"}""").mkString(",\n")}
  ],
  "signature": "${demoData.signature}",
  "signatureType": "${demoData.signatureType}",
  "publicKey": "${demoData.publicKey}"
}"""
        os.write(demoFilePath, jsonContent)
        log.info(s"$wid Demo file generated: $demoFilePath")

        if (input.waitForConfirmation) {
          log.info(s"$wid Please confirm to use file: $demoFilePath (signalMode=${input.signalMode})")
          waitForUserSignal(workflowId, wid, input.signalMode)
        }

        // PoL MERGE STRATEGY: Never trust previous output, always override with fresh input
        run.output.pol.foreach { previousOutput =>
          log.info(s"$wid PoL: Ignoring previous output (${previousOutput.liabilities.size} entries), using fresh data")
        }

        val output = PolOutput(
          ts = demoData.ts,
          liabilities = demoData.liabilities,
          signature = demoData.signature,
          signatureType = demoData.signatureType,
          publicKey = demoData.publicKey
        )

        log.info(s"$wid Completed PoL with ${output.liabilities.size} liability entries")
        run.copy(output = run.output.copy(pol = Some(output)))
    }
  }

  private def generateDemoLiabilitiesFile(): PolFileData = {
    val liabilities = (1 to 10).map { i =>
      Liability(
        userId = UUID.randomUUID(),
        asset = Seq("BTC", "ETH", "LINK", "AAVE", "SOL")(Random.nextInt(5)),
        balance = BigInt(Random.nextInt(500000)) * BigInt(10).pow(18)
      )
    }.toList

    PolFileData(
      ts = System.currentTimeMillis(),
      liabilities = liabilities,
      signature = s"0x${Random.alphanumeric.take(128).mkString}",
      signatureType = "public_key",
      publicKey = s"0x${Random.alphanumeric.take(64).mkString}"
    )
  }
}
