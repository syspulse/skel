package io.syspulse.skel.wf.temporal.workflow

import com.typesafe.scalalogging.Logger
import io.temporal.workflow.{DynamicWorkflow, DynamicQueryHandler, DynamicSignalHandler, Workflow}
import io.temporal.common.converter.EncodedValues

import io.hacken.ext.wf.WorkflowRun

/**
 * Dynamic Workflow Implementation
 *
 * Handles workflows with custom type names (from WorkflowSchema.name)
 * Delegates actual execution to GenericWorkflowImpl logic
 * Registers dynamic query and signal handlers
 */
class DynamicWorkflowImpl extends DynamicWorkflow {

  private val log = Logger(getClass)

  // Delegate to GenericWorkflowImpl for actual logic
  private val impl = new GenericWorkflowImpl()

  // Register dynamic query handler
  Workflow.registerListener(new DynamicQueryHandler {
    override def handle(queryName: String, args: EncodedValues): Object = {
      log.info(s"Handling query: ${queryName}")
      queryName match {
        case "getWorkflowRun" => impl.getWorkflowRun()
        case "getStatus" => impl.getStatus()
        case "getCursor" => Int.box(impl.getCursor())
        case _ =>
          log.warn(s"Unknown query type: ${queryName}")
          throw new IllegalArgumentException(s"Unknown query type: ${queryName}")
      }
    }
  })

  // Register dynamic signal handler
  Workflow.registerListener(new DynamicSignalHandler {
    override def handle(signalName: String, args: EncodedValues): Unit = {
      log.info(s"Handling signal: ${signalName}")
      signalName match {
        case "continueWorkflow" =>
          val configId = args.get(0, classOf[Integer]).intValue()
          impl.continueWorkflow(configId)
        case _ =>
          log.warn(s"Unknown signal type: ${signalName}")
      }
    }
  })

  /**
   * Execute workflow with dynamic type name
   *
   * This allows workflow types in Temporal UI to show business names
   * (e.g., "PoR2 Workflow", "KYC Workflow") instead of "GenericWorkflow"
   */
  override def execute(args: EncodedValues): Object = {
    val workflowType = Workflow.getInfo().getWorkflowType
    log.info(s"Executing dynamic workflow: type=${workflowType}")

    // Decode the WorkflowRun argument
    val run = args.get(0, classOf[WorkflowRun])
    log.info(s"WorkflowRun: wid=${run.wid}, schema=${run.schema}, steps=${run.steps.size}")

    // Execute using GenericWorkflowImpl logic
    val result = impl.execute(run)
    log.info(s"Workflow ${workflowType} completed: status=${result.status}")

    result
  }
}
