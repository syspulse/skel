package io.syspulse.skel.wf.temporal.por2

import com.typesafe.scalalogging.Logger
import spray.json._

import io.hacken.ext.detector.DetectorConfig
import io.syspulse.skel.wf.temporal.workflow.activity.GenericActivitiesImpl
import io.syspulse.skel.wf.temporal.workflow.store.{WorkflowSchemaStore, WorkflowRunStore, WorkflowConfigStore}

import scala.util.{Success, Failure}

/**
 * PoR2-specific Activities Implementation
 *
 * Extends GenericActivitiesImpl to provide PoR-specific activity implementations.
 * Delegates to Por2Activities for the actual PoR logic.
 */
class Por2ActivitiesImpl(
  schemaStore: WorkflowSchemaStore,
  runStore: WorkflowRunStore,
  configStore: WorkflowConfigStore
) extends GenericActivitiesImpl(schemaStore, runStore, configStore) {

  private val por2Log = Logger(getClass)

  /**
   * Override executeActivity to provide PoR-specific implementations
   */
  override def executeActivity(configId: Int): io.syspulse.skel.wf.temporal.workflow.activity.ActivityResult = {
    por2Log.info(s"Executing PoR2 activity for config: ${configId}")

    // Fetch config from store
    val config = configStore.???(configId) match {
      case Success(c) => c
      case Failure(e) =>
        log.error(s"Failed to get detector config ${configId}: ${e.getMessage}")
        throw e
    }

    por2Log.info(s"Executing PoR2 activity: ${config.name}")

    try {
      // Map activity name to PoR-specific implementation
      val result = config.name.toLowerCase match {
        case "proofofownership" | "poo" =>
          Por2Activities.executeProofOfOwnership(config)

        case "proofofreserve" | "por" =>
          Por2Activities.executeProofOfReserve(config)

        case "proofofliability" | "pol" =>
          Por2Activities.executeProofOfLiability(config)

        case "solvency" =>
          Por2Activities.executeSolvency(config)

        case "report" =>
          Por2Activities.executeReport(config)

        case "commit" =>
          Por2Activities.executeCommit(config)

        case _ =>
          // Fall back to generic implementation
          por2Log.warn(s"Unknown PoR2 activity: ${config.name}, using generic implementation")
          executeGenericActivity(config)
      }

      // Store updated config
      configStore.+(result) match {
        case Success(updated) =>
          por2Log.info(s"PoR2 activity ${config.name} completed successfully")

          // Extract output JsObject if present
          val output = updated.config
            .flatMap(_.asJsObject.fields.get("output"))
            .collect { case obj: JsObject => obj }
            .getOrElse(JsObject())

          // Build ActivityResult
          io.syspulse.skel.wf.temporal.workflow.activity.ActivityResult.success(updated.id, updated.name, output)
        case Failure(e) =>
          log.error(s"Failed to store config: ${e.getMessage}")
          throw e
      }

    } catch {
      case e: Exception =>
        log.error(s"PoR2 activity ${config.name} failed: ${e.getMessage}", e)
        throw e
    }
  }
}
