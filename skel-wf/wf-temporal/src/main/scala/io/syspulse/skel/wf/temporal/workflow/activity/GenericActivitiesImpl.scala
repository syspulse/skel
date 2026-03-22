package io.syspulse.skel.wf.temporal.workflow.activity

import com.typesafe.scalalogging.Logger
import spray.json._

import io.hacken.ext.wf.{WorkflowRun, WorkflowSchema}
import io.hacken.ext.detector.{DetectorConfig, DetectorConfigJson}
import io.syspulse.skel.wf.temporal.workflow.store.{WorkflowSchemaStore, WorkflowRunStore, WorkflowConfigStore}
import io.syspulse.skel.wf.temporal.por2.Por2Activities

import scala.util.{Success, Failure}

/**
 * Generic Activities Implementation
 *
 * Uses stores to manage workflow data
 */
class GenericActivitiesImpl(
  schemaStore: WorkflowSchemaStore,
  runStore: WorkflowRunStore,
  configStore: WorkflowConfigStore
) extends GenericActivities {

  private val log = Logger(getClass)

  override def getWorkflowSchema(schemaId: Int): WorkflowSchema = {
    log.info(s"Getting workflow schema: ${schemaId}")
    schemaStore.???(schemaId) match {
      case Success(schema) => schema
      case Failure(e) =>
        log.error(s"Failed to get workflow schema ${schemaId}: ${e.getMessage}")
        throw e
    }
  }

  override def getDetectorConfig(configId: Int): Int = {
    log.info(s"Getting detector config: ${configId}")
    configStore.???(configId) match {
      case Success(config) => config.id
      case Failure(e) =>
        log.error(s"Failed to get detector config ${configId}: ${e.getMessage}")
        throw e
    }
  }

  override def getDetectorConfigName(configId: Int): String = {
    log.info(s"Getting detector config name: ${configId}")
    configStore.???(configId) match {
      case Success(config) =>
        log.info(s"Config name for ${configId}: ${config.name}")
        config.name
      case Failure(e) =>
        log.error(s"Failed to get detector config ${configId}: ${e.getMessage}")
        throw e
    }
  }

  override def updateWorkflowRun(run: WorkflowRun): WorkflowRun = {
    log.info(s"Updating workflow run: ${run.rid.getOrElse(run.wid)}, status=${run.status}, cursor=${run.cursor}")
    runStore.+(run) match {
      case Success(updated) => updated
      case Failure(e) =>
        log.error(s"Failed to update workflow run: ${e.getMessage}")
        throw e
    }
  }

  override def getStepType(configId: Int): String = {
    log.info(s"Getting step type for config: ${configId}")
    val config = configStore.???(configId) match {
      case Success(c) => c
      case Failure(e) =>
        log.error(s"Failed to get detector config ${configId}: ${e.getMessage}")
        throw e
    }

    val stepType = DetectorConfig.getString(config, "type", "AUTO")
    log.info(s"Step type for ${config.name}: ${stepType}")
    stepType
  }

  override def executeActivity(configId: Int): Int = {
    log.info(s"Executing activity for config: ${configId}")

    // Fetch config from store
    val config = configStore.???(configId) match {
      case Success(c) => c
      case Failure(e) =>
        log.error(s"Failed to get detector config ${configId}: ${e.getMessage}")
        throw e
    }

    log.info(s"Executing activity: ${config.name}")

    try {
      // Map activity name to implementation
      val result = config.name.toLowerCase match {
        case "proofofownership" | "poo" =>
          executeProofOfOwnership(config)

        case "proofofreserve" | "por" =>
          executeProofOfReserve(config)

        case "proofofliability" | "pol" =>
          executeProofOfLiability(config)

        case "solvency" =>
          executeSolvency(config)

        case "report" =>
          executeReport(config)

        case "commit" =>
          executeCommit(config)

        case _ =>
          log.warn(s"Unknown activity: ${config.name}, executing as generic")
          executeGenericActivity(config)
      }

      // Store updated config
      configStore.+(result) match {
        case Success(updated) =>
          log.info(s"Activity ${config.name} completed successfully")
          updated.id
        case Failure(e) =>
          log.error(s"Failed to store config: ${e.getMessage}")
          throw e
      }

    } catch {
      case e: Exception =>
        log.error(s"Activity ${config.name} failed: ${e.getMessage}", e)
        throw e
    }
  }

  /**
   * Execute Proof of Ownership activity
   */
  private def executeProofOfOwnership(config: DetectorConfig): DetectorConfig = {
    log.info(s"Executing Proof of Ownership")

    // Delegate to PoR2 activities for better implementation
    if (config.source == "POR2") {
      Por2Activities.executeProofOfOwnership(config)
    } else {
      // Get wallets from config
      val wallets = DetectorConfig.getArrayMap(config, "wallets", Vector.empty)
      log.info(s"Processing ${wallets.size} wallets")

      // Simulate ownership verification
      val verified = wallets.map { wallet =>
        Map(
          "address" -> wallet.getOrElse("address", "unknown"),
          "verified" -> true,
          "timestamp" -> System.currentTimeMillis()
        )
      }

      // Store output
      val output = JsObject(
        "verified" -> JsArray(verified.map(w => JsString(w("address").toString))),
        "total" -> JsNumber(verified.size),
        "timestamp" -> JsNumber(System.currentTimeMillis())
      )

      addOutput(config, output)
    }
  }

  /**
   * Execute Proof of Reserve activity
   */
  private def executeProofOfReserve(config: DetectorConfig): DetectorConfig = {
    log.info(s"Executing Proof of Reserve")

    // Delegate to PoR2 activities for better implementation
    if (config.source == "POR2") {
      Por2Activities.executeProofOfReserve(config)
    } else {
      // Simulate reserve calculation
      val reserves = 1000000.0 // Example value

      val output = JsObject(
        "reserves" -> JsNumber(reserves),
        "currency" -> JsString("USD"),
        "timestamp" -> JsNumber(System.currentTimeMillis())
      )

      addOutput(config, output)
    }
  }

  /**
   * Execute Proof of Liability activity
   */
  private def executeProofOfLiability(config: DetectorConfig): DetectorConfig = {
    log.info(s"Executing Proof of Liability")

    // Delegate to PoR2 activities for better implementation
    if (config.source == "POR2") {
      Por2Activities.executeProofOfLiability(config)
    } else {
      // Get liabilities from config
      val liabilities = DetectorConfig.getArrayMap(config, "liabilities", Vector.empty)
      log.info(s"Processing ${liabilities.size} liabilities")

      val totalLiability = liabilities.map { l =>
        l.get("amount").map(_.toString.toDouble).getOrElse(0.0)
      }.sum

      val output = JsObject(
        "liabilities" -> JsNumber(totalLiability),
        "count" -> JsNumber(liabilities.size),
        "timestamp" -> JsNumber(System.currentTimeMillis())
      )

      addOutput(config, output)
    }
  }

  /**
   * Execute Solvency activity
   */
  private def executeSolvency(config: DetectorConfig): DetectorConfig = {
    log.info(s"Executing Solvency")

    // Delegate to PoR2 activities for better implementation
    if (config.source == "POR2") {
      Por2Activities.executeSolvency(config)
    } else {
      // TODO: Get reserves and liabilities from previous steps
      val reserves = 1000000.0
      val liabilities = 800000.0
      val solvencyRatio = reserves / liabilities

      val output = JsObject(
        "reserves" -> JsNumber(reserves),
        "liabilities" -> JsNumber(liabilities),
        "solvency_ratio" -> JsNumber(solvencyRatio),
        "solvent" -> JsBoolean(solvencyRatio >= 1.0),
        "timestamp" -> JsNumber(System.currentTimeMillis())
      )

      addOutput(config, output)
    }
  }

  /**
   * Execute Report activity
   */
  private def executeReport(config: DetectorConfig): DetectorConfig = {
    log.info(s"Executing Report")

    // Delegate to PoR2 activities for better implementation
    if (config.source == "POR2") {
      Por2Activities.executeReport(config)
    } else {
      val output = JsObject(
        "report_generated" -> JsBoolean(true),
        "report_id" -> JsString(s"report-${System.currentTimeMillis()}"),
        "timestamp" -> JsNumber(System.currentTimeMillis())
      )

      addOutput(config, output)
    }
  }

  /**
   * Execute Commit activity
   */
  private def executeCommit(config: DetectorConfig): DetectorConfig = {
    log.info(s"Executing Commit")

    // Delegate to PoR2 activities for better implementation
    if (config.source == "POR2") {
      Por2Activities.executeCommit(config)
    } else {
      val output = JsObject(
        "committed" -> JsBoolean(true),
        "commit_id" -> JsString(s"commit-${System.currentTimeMillis()}"),
        "timestamp" -> JsNumber(System.currentTimeMillis())
      )

      addOutput(config, output)
    }
  }

  /**
   * Execute generic activity (unknown activities)
   */
  private def executeGenericActivity(config: DetectorConfig): DetectorConfig = {
    log.info(s"Executing generic activity: ${config.name}")

    val output = JsObject(
      "activity" -> JsString(config.name),
      "executed" -> JsBoolean(true),
      "timestamp" -> JsNumber(System.currentTimeMillis())
    )

    addOutput(config, output)
  }

  /**
   * Add output to DetectorConfig
   */
  private def addOutput(config: DetectorConfig, output: JsObject): DetectorConfig = {
    val currentConfig = config.config.getOrElse(JsObject())
    val updatedConfig = JsObject(
      currentConfig.fields + ("output" -> output)
    )

    config.copy(config = Some(updatedConfig))
  }
}
