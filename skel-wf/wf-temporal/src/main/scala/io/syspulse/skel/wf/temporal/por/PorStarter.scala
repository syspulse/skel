package io.syspulse.skel.wf.temporal.por

import scala.util.{Try,Success,Failure}

import io.temporal.client.{WorkflowClient, WorkflowOptions}
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.common.{SearchAttributeKey, SearchAttributes}
import io.syspulse.skel.wf.temporal.{ScalaDataConverter, TemporalURI}
import com.typesafe.scalalogging.Logger
import scala.jdk.CollectionConverters._

case class PorConfig(
  ownerName: String = "DefaultOwner",
  flow: String = "flow-1",
  pooRequired: Boolean = true,
  porRequired: Boolean = true,
  polRequired: Boolean = true,
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

  def run(uri: String, config: PorConfig): Try[String] = {
    try {

      val t = TemporalURI(uri)
      log.info(s"Connecting to Temporal at ${t.target} namespace=${t.namespace}")

      val serviceOptions = io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
        .setTarget(t.target)
        .setEnableKeepAlive(t.enableKeepAlive)
        .setKeepAliveTime(java.time.Duration.ofSeconds(t.keepAliveTimeSec))
        .setKeepAliveTimeout(java.time.Duration.ofSeconds(t.keepAliveTimeoutSec))
        .setRpcTimeout(java.time.Duration.ofSeconds(t.rpcTimeoutSec))
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
        pooRequired = config.pooRequired,
        porRequired = config.porRequired,
        polRequired = config.polRequired,
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

      log.info(s"Starting PoR Workflow: $wid: ownerName=${config.ownerName} flow=${config.flow} polSignalMode=${config.polSignalMode} poo=${config.pooRequired} por=${config.porRequired} pol=${config.polRequired} report=${config.reportRequired}")

      val result = workflow.execute(input)

      log.info(s"$wid: Report=${result.reportFilePath}, link=${result.reportLink}")    

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
