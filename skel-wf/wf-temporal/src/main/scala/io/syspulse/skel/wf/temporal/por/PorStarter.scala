package io.syspulse.skel.wf.temporal.por

import scala.util.{Try,Success,Failure}

import io.temporal.client.{WorkflowClient, WorkflowOptions}
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.common.{SearchAttributeKey, SearchAttributes}
import io.syspulse.skel.wf.temporal.{ScalaDataConverter, TemporalURI}
import com.typesafe.scalalogging.Logger
import scala.jdk.CollectionConverters._

object PorStarter {
  private val log = Logger(getClass.getName)

  def run(uri: String, run: PorWorkflowRun): Try[String] = {
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

      val wid = s"por-workflow-${run.ownerName}-${run.ts0}"

      // Build workflow options
      val optionsBuilder = WorkflowOptions.newBuilder()
        .setWorkflowId(wid)
        .setTaskQueue(PorWorker.TASK_QUEUE)

      // Add memo if tags or custom memo are present
      if (run.tags.nonEmpty || run.memo.nonEmpty) {
        val memoMap = scala.collection.mutable.Map.empty[String, Object]

        // Add tags to memo
        if (run.tags.nonEmpty) {
          memoMap += ("tags" -> run.tags.asJava.asInstanceOf[Object])
          log.info(s"$wid: Adding tags to memo: ${run.tags.mkString("[", ", ", "]")}")
        }

        // Add custom memo entries
        if (run.memo.nonEmpty) {
          run.memo.foreach { case (key, value) =>
            memoMap += (key -> value.asInstanceOf[Object])
          }
          log.info(s"$wid: Adding custom memo: ${run.memo.map { case (k, v) => s"$k=$v" }.mkString(", ")}")
        }

        optionsBuilder.setMemo(memoMap.asJava)
      }

      // Add search attributes if tags are present
      // Note: CustomKeywordField search attribute must be registered in Temporal
      if (run.tags.nonEmpty) {
        val searchAttrKey = SearchAttributeKey.forKeywordList("CustomKeywordField")
        val searchAttributes = SearchAttributes.newBuilder()
          .set(searchAttrKey, run.tags.asJava)
          .build()
        optionsBuilder.setTypedSearchAttributes(searchAttributes)
        log.info(s"$wid: Adding tags to search attributes: ${run.tags.mkString("[", ", ", "]")}")
      }

      val options = optionsBuilder.build()

      val workflow = client.newWorkflowStub(classOf[PorWorkflow], options)

      log.info(s"Starting PoR Workflow: $wid: ownerName=${run.ownerName}: input=${run.input}")

      val result = workflow.execute(run)

      result.output.report match {
        case Some(report) =>
          log.info(s"$wid: Report=${report.reportFilePath}, link=${report.reportLink}")
        case None =>
          log.info(s"$wid: Workflow completed without report output")
      }

      result.output.commit.foreach { commit =>
        log.info(s"$wid: Output committed to: ${commit.filePath}")
      }

      log.info(s"$wid: Completed - PoO=${result.output.poo.isDefined}, PoR=${result.output.por.isDefined}, PoL=${result.output.pol.isDefined}, Solvency=${result.output.solvency.isDefined}, Report=${result.output.report.isDefined}, Commit=${result.output.commit.isDefined}")

      service.shutdown()
      Success(wid)
    }
    catch {
      case e: Exception =>
        log.error(s"Failed to start Workflow: ${run.ownerName}: ${e.getMessage}", e)
        Failure(e)
    }
  }
}
