package io.syspulse.skel.wf.temporal.por

import scala.util.{Try,Success,Failure}
import scala.concurrent.{Future, ExecutionContext}

import io.temporal.client.{WorkflowClient, WorkflowOptions}
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.common.{SearchAttributeKey, SearchAttributes}
import io.syspulse.skel.wf.temporal.{ScalaDataConverter, TemporalURI}
import com.typesafe.scalalogging.Logger
import scala.jdk.CollectionConverters._

case class PorStartResult(workflowId: String, runId: String)

object PorStarter {
  private val log = Logger(getClass.getName)

  val SYS_KEY = "sys"
  val PROJ_KEY = "proj"
  val TID_KEY = "tid"
  val PID_KEY = "pid"

  def run(uri: String, run: PorWorkflowRun, taskQueue: String = PorWorker.TASK_QUEUE)(implicit ec: ExecutionContext): Future[PorStartResult] = Future {

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

      val wid0 = s"por-workflow-${run.proj}-${run.ts0}"

      // Build workflow options
      val optionsBuilder = WorkflowOptions.newBuilder()
        .setWorkflowId(wid0)
        .setTaskQueue(taskQueue)

      // Add memo if tags or custom memo are present
      if (run.tags.nonEmpty || run.memo.nonEmpty) {
        val memoMap = scala.collection.mutable.Map.empty[String, Object]

        // Add tags to memo
        if (run.tags.nonEmpty) {
          memoMap += ("tags" -> run.tags.asJava.asInstanceOf[Object])
          log.info(s"$wid0: Adding tags to memo: ${run.tags.mkString("[", ", ", "]")}")
        }

        // Add custom memo entries
        if (run.memo.nonEmpty) {
          run.memo.foreach { case (key, value) =>
            memoMap += (key -> value.asInstanceOf[Object])
          }
          log.info(s"$wid0: Adding custom memo: ${run.memo.map { case (k, v) => s"$k=$v" }.mkString(", ")}")
        }

        optionsBuilder.setMemo(memoMap.asJava)
      }

      // Build search attributes (indexed fields for efficient querying)
      val searchAttrsBuilder = SearchAttributes.newBuilder()
      var hasSearchAttrs = false

      // Add tid (Tenant ID) if present
      run.tid.foreach { tid =>
        val tidKey = SearchAttributeKey.forLong(TID_KEY).asInstanceOf[SearchAttributeKey[Any]]
        searchAttrsBuilder.set(tidKey, tid.toLong.asInstanceOf[Any])
        hasSearchAttrs = true
        log.info(s"$wid0: tid=$tid")
      }

      // Add pid (Project ID) if present
      run.pid.foreach { pid =>
        val pidKey = SearchAttributeKey.forLong(PID_KEY).asInstanceOf[SearchAttributeKey[Any]]
        searchAttrsBuilder.set(pidKey, pid.toLong.asInstanceOf[Any])
        hasSearchAttrs = true
        log.info(s"$wid0: pid=$pid")
      }

      // Add proj (Project/System name)
      if (run.sys.nonEmpty) {
        val sysKey = SearchAttributeKey.forKeyword(SYS_KEY)
        searchAttrsBuilder.set(sysKey, run.sys.get)
        hasSearchAttrs = true
        log.info(s"$wid0: sys=${run.sys}")
      }

      if (run.proj.nonEmpty) {
        val projKey = SearchAttributeKey.forKeyword(PROJ_KEY)
        searchAttrsBuilder.set(projKey, run.proj.get)
        hasSearchAttrs = true
        log.info(s"$wid0: proj=${run.proj}")
      }

      // Add tags if present (using CustomKeywordField)
      if (run.tags.nonEmpty) {
        val tagsKey = SearchAttributeKey.forKeywordList("CustomKeywordField")
        searchAttrsBuilder.set(tagsKey, run.tags.asJava)
        hasSearchAttrs = true
        log.info(s"$wid0: Stags=${run.tags.mkString("[", ", ", "]")}")
      }

      // Set search attributes if any were added
      if (hasSearchAttrs) {
        optionsBuilder.setTypedSearchAttributes(searchAttrsBuilder.build())
      }

      val options = optionsBuilder.build()

      val workflow = client.newWorkflowStub(classOf[PorWorkflow], options)

      log.info(s"Starting Workflow: $wid0: sys=${run.sys}, proj=${run.proj}, tags=${run.tags}, input=${run.input}")

      // Start workflow asynchronously
      val execution = WorkflowClient.start(workflow.execute _, run)
      val runId = execution.getRunId

      log.info(s"Workflow: [$wid0 / $runId]")

      service.shutdown()

      PorStartResult(wid0, runId)
  }
}
