package io.syspulse.skel.wf.temporal.por

import scala.util.{Try,Success,Failure}

import io.temporal.client.{WorkflowClient, WorkflowOptions}
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.common.{SearchAttributeKey, SearchAttributes}
import io.syspulse.skel.wf.temporal.{ScalaDataConverter, TemporalURI}
import com.typesafe.scalalogging.Logger
import scala.jdk.CollectionConverters._

/**
 * Configuration for running a PoR workflow
 * Supports incremental runs by providing previous outputs and new inputs
 */
case class PorRunConfig(
  ownerName: String = "DefaultOwner",
  flow: String = "flow-1",

  // PoO step - provide either input (to execute) or output (to reuse)
  pooInput: Option[PooInput] = None,
  pooOutput: Option[PooOutput] = None,

  // PoR step - provide either input (to execute) or output (to reuse)
  porInput: Option[PorInput] = None,
  porOutput: Option[PorOutput] = None,

  // PoL step - provide either input (to execute) or output (to reuse)
  polInput: Option[PolInput] = None,
  polOutput: Option[PolOutput] = None,

  // Report generation
  reportRequired: Boolean = true,

  /** PoL user signal: file | rest | simulate (default) */
  polSignalMode: String = "simulate",

  /** Tags for workflow metadata and search attributes (e.g., ["CEX", "Bybit"]) */
  tags: Seq[String] = Seq.empty,

  /** Additional memo data for workflow (key=value pairs, e.g., ["region=US", "env=prod"]) */
  memo: Map[String, String] = Map.empty
)

object PorStarter {
  private val log = Logger(getClass.getName)

  def run(uri: String, config: PorRunConfig): Try[String] = {
    try {

      val t = TemporalURI(uri)
      log.info(s"Connecting to Temporal -> ${t.target} (namespace=${t.namespace})")

      val serviceOptions = io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
        .setTarget(t.target)
        .setEnableKeepAlive(t.enableKeepAlive)
      .setKeepAliveTime(java.time.Duration.ofMillis(t.keepAliveTime))
      .setKeepAliveTimeout(java.time.Duration.ofMillis(t.keepAliveTimeout))
      .setRpcTimeout(java.time.Duration.ofMillis(t.rpcTimeout))
        .build()

      val service = WorkflowServiceStubs.newServiceStubs(serviceOptions)

      val clientOptions = io.temporal.client.WorkflowClientOptions.newBuilder()
        .setNamespace(t.namespace)
        .setDataConverter(ScalaDataConverter.create())
        .build()

      val client = WorkflowClient.newInstance(service, clientOptions)

      val input = PorWorkflowInput(
        ownerName = config.ownerName,
        timestamp = System.currentTimeMillis(),
        pooInput = config.pooInput,
        pooOutput = config.pooOutput,
        porInput = config.porInput,
        porOutput = config.porOutput,
        polInput = config.polInput,
        polOutput = config.polOutput,
        reportRequired = config.reportRequired,
        polSignalMode = config.polSignalMode
      )

      val wid = s"por-workflow-${config.ownerName}-${System.currentTimeMillis()}"

      // Build workflow options
      val optionsBuilder = WorkflowOptions.newBuilder()
        .setWorkflowId(wid)
        .setTaskQueue(PorWorker.TASK_QUEUE)

      // Add memo if tags or custom memo are present
      if (config.tags.nonEmpty || config.memo.nonEmpty) {
        val memoMap = scala.collection.mutable.Map.empty[String, Object]

        // Add tags to memo
        if (config.tags.nonEmpty) {
          memoMap += ("tags" -> config.tags.asJava.asInstanceOf[Object])
          log.info(s"$wid: Adding tags to memo: ${config.tags.mkString("[", ", ", "]")}")
        }

        // Add custom memo entries
        if (config.memo.nonEmpty) {
          config.memo.foreach { case (key, value) =>
            memoMap += (key -> value.asInstanceOf[Object])
          }
          log.info(s"$wid: Adding custom memo: ${config.memo.map { case (k, v) => s"$k=$v" }.mkString(", ")}")
        }

        optionsBuilder.setMemo(memoMap.asJava)
      }

      // Add search attributes if tags are present
      // Note: CustomKeywordField search attribute must be registered in Temporal
      if (config.tags.nonEmpty) {
        val searchAttrKey = SearchAttributeKey.forKeywordList("CustomKeywordField")
        val searchAttributes = SearchAttributes.newBuilder()
          .set(searchAttrKey, config.tags.asJava)
          .build()
        optionsBuilder.setTypedSearchAttributes(searchAttributes)
        log.info(s"$wid: Adding tags to search attributes: ${config.tags.mkString("[", ", ", "]")}")
      }

      val options = optionsBuilder.build()

      val workflow = client.newWorkflowStub(classOf[PorWorkflow], options)

      log.info(s"Starting PoR Workflow: $wid: ownerName=${config.ownerName} flow=${config.flow} polSignalMode=${config.polSignalMode} pooIn=${config.pooInput.isDefined} pooOut=${config.pooOutput.isDefined} porIn=${config.porInput.isDefined} porOut=${config.porOutput.isDefined} polIn=${config.polInput.isDefined} polOut=${config.polOutput.isDefined} report=${config.reportRequired}")

      val result = workflow.execute(input)

      result.reportOutput match {
        case Some(report) =>
          log.info(s"$wid: Report=${report.reportFilePath}, link=${report.reportLink}")
        case None =>
          log.info(s"$wid: Workflow completed without report output")
      }

      log.info(s"$wid: Completed - PoO=${result.pooOutput.isDefined}, PoR=${result.porOutput.isDefined}, PoL=${result.polOutput.isDefined}, Solvency=${result.solvencyOutput.isDefined}, Report=${result.reportOutput.isDefined}")

      service.shutdown()
      Success(wid)
    }
    catch {
      case e: Exception =>
        log.error(s"Failed to start Workflow: ${config}: ${e.getMessage}", e)
        Failure(e)
    }
  }
}
