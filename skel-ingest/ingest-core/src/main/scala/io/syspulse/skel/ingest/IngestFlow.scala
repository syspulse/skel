package io.syspulse.skel.ingest

import scala.jdk.CollectionConverters._
import java.util.concurrent.TimeUnit
import com.typesafe.scalalogging.Logger
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global 

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, BroadcastHub}
import akka.{Done, NotUsed}
import akka.util.ByteString
import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._
import akka.event.Logging

import spray.json._

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

import io.syspulse.skel.Ingestable
import io.syspulse.skel.util.Util
import scala.util.Success
import scala.util.Failure

trait IngestFlowPipeline[I,T,O] extends IngestFlow[I,T,O] {  
  implicit val system = ActorSystem("ActorSystem-IngestFlow")
}

// Source[ByteString] -> [ByteString] InputObject [I] -> [I] Process [T] -> [T] TransformedObject [O] -> Sink[O]
trait IngestFlow[I,T,O] {
  private val log = Logger(s"${this}")
  
  //implicit val system = ActorSystem("ActorSystem-IngestFlow")
  implicit val system:ActorSystem;//as.getOrElse(ActorSystem("ActorSystem-IngestFlow"))

  def ingestFlowName() = "ingest-flow"
  
  def retrySettings:Option[RestartSettings] = 
    Some(RestartSettings(
      minBackoff = FiniteDuration(3000,TimeUnit.MILLISECONDS),
      maxBackoff = FiniteDuration(10000,TimeUnit.MILLISECONDS),
      randomFactor = 0.2
    ))
    //.withMaxRestarts(10, 5.minutes)
  
  // used by Flow.log()
  def logLevels = Attributes.createLogLevels(
    Logging.DebugLevel, 
    onFinish = Logging.InfoLevel, //Logging.DebugLevel, 
    onFailure = Logging.ErrorLevel
  )

  val cr = new CollectorRegistry(true);
  val countBytes: Counter = Counter.build().name("ingest_bytes").help("total bytes").register(cr)
  val countInput: Counter = Counter.build().name("ingest_input").help("input data").register(cr)
  val countObj: Counter = Counter.build().name("ingest_obj").help("total objects")register(cr)
  val countOutput: Counter = Counter.build().name("ingest_output").help("output data").register(cr)

  var defaultSource:Option[Source[ByteString,_]] = None
  var defaultSink:Option[Sink[O,_]] = None

  // Track if this flow is already running (for connectTo chaining)
  private var isRunning: Boolean = false
  private var outputSourceCache: Option[Source[O, _]] = None

  def parse(data:String):Seq[I]

  def sink():Sink[O,Any] = if(defaultSink.isDefined) defaultSink.get else Sink.foreach(println _)

  def sink0():Sink[O,Any] = Sink.ignore

  def source():Source[ByteString,_] = if(defaultSource.isDefined) defaultSource.get else StreamConverters.fromInputStream(() => System.in)

  // flow shaping (throttle, etc)
  def shaping:Flow[T,T,_]

  def process:Flow[I,T,_]

  def transform(t:T):Seq[O]

  //def formatter:Flow[O,Any,_]

  def debug = Flow.fromFunction( (data:ByteString) => { log.trace(s"data=${data}"); data})

  def counterBytes = Flow[ByteString].map(t => { countBytes.inc(t.size); t})
  def counterI = Flow[I].map(t => { countInput.inc(); t})
  def counterT = Flow[T].map(t => { countObj.inc(); t})
  def counterO = Flow[O].map(t => { countOutput.inc(); t})

  def run() = {
    val f0 = source()
      // .log(ingestFlowName()).withAttributes(logLevels)
      .via(debug)
      .via(counterBytes)      
      .mapConcat(txt => {
        // ATTENTION: replace with ByteString because it is impossible to properly parse BinaryData !
        parse(txt.utf8String)
      })
      .via(counterI)
    
    val f1 = if(retrySettings.isDefined) {
      RestartSource.onFailuresWithBackoff(retrySettings.get) { () =>
        log.info(s"source retry: ${retrySettings.get}")
        f0
      } 
    }
    else
      f0

    val f2 = f1
      .via(process)
      .via(shaping)
      .via(counterT)
      .viaMat(KillSwitches.single)(Keep.both)
      .mapConcat(t => {
        try {
          transform(t)
        } catch {
          case e:Exception =>
            log.warn(s"failed to transform: ${t}",e)
            Seq[O]()
        }
      })
      .via(counterO)
      //.via(formatter)
      .log(ingestFlowName()).withAttributes(logLevels)
      .alsoTo(sink0())
    
    val f3 = f2

    val s0 = sink()

    val s1 = if(retrySettings.isDefined) {
      RestartSink.withBackoff(retrySettings.get) { () =>
        log.info(s"sink retry: ${retrySettings.get}")
        s0
      } 
    }
    else
      s0

    // val errorSupervisor: Supervision.Decider = {
    //   case e: java.lang.RuntimeException =>
    //     log.error("",e)
    //     Supervision.Resume
    //   case e: Exception =>
    //     log.error("",e)          
    //     Supervision.Resume
    // }

    val mat = f3
      // .withAttributes(
      //   ActorAttributes.supervisionStrategy(errorSupervisor)
      // )      
      //.runWith(s1)
      .watchTermination()((_, termination) => termination)
      .to(s1)
      .run()        

    // mat.onComplete { 
    //   case Success(_) => 
    //     log.debug("")        
    //   case Failure(ex) => 
    //     log.debug("", ex)
    //   }
      
    log.debug(s"f1=${f1}: f2=${f2}: graph: ${f3}: flow=${mat}")
    mat
  }

  def from(src:Source[ByteString,_]):IngestFlow[I,T,O] = {
    defaultSource = Some(src)
    this
  }

  /**
   * Connect this flow's output to another flow's input.
   * Automatically starts this flow if not already running.
   * Returns the next flow for chaining.
   * 
   * Usage:
   *   flow1.connectTo(flow2).connectTo(flow3)
   *   flow3.run()  // Only need to run the last flow
   */
  def connectTo[O2 <: Ingestable](
    nextFlow: IngestFlow[_, _, O2]
  )(implicit fmt: JsonFormat[O], ev: O <:< Ingestable): IngestFlow[_, _, O2] = {
    // Get or create output Source (starts flow if not running)
    val outputSource = getOrCreateOutputSource()
    
    // Convert Source[O, _] to Source[ByteString, _] by serializing to JSON
    val byteStringSource = outputSource.map { o =>
      ByteString(o.toJson.compactPrint + "\n")
    }
    
    // Connect to next flow
    nextFlow.from(byteStringSource)
    nextFlow
  }

  /**
   * Get or create the output Source for this flow.
   * If the flow is not running, starts it automatically via BroadcastHub.
   */
  private def getOrCreateOutputSource(): Source[O, _] = {
    if (isRunning && outputSourceCache.isDefined) {
      outputSourceCache.get
    } else {
      val source = runToSource()
      isRunning = true
      outputSourceCache = Some(source)
      source
    }
  }

  /**
   * Create a BroadcastHub Source from this flow's output.
   * This materializes and runs the flow, connecting it to its sink
   * while also exposing the output as a reusable Source.
   */
  private def runToSource(): Source[O, _] = {
    val f0 = source()
      .via(debug)
      .via(counterBytes)      
      .mapConcat(txt => {
        // ATTENTION: replace with ByteString because it is impossible to properly parse BinaryData !
        parse(txt.utf8String)
      })
      .via(counterI)
    
    val f1 = if(retrySettings.isDefined) {
      RestartSource.onFailuresWithBackoff(retrySettings.get) { () =>
        log.info(s"source retry: ${retrySettings.get}")
        f0
      } 
    } else {
      f0
    }

    val outputFlow = f1
      .via(process)
      .via(shaping)
      .via(counterT)
      .viaMat(KillSwitches.single)(Keep.both)
      .mapConcat(t => {
        try {
          transform(t)
        } catch {
          case e:Exception =>
            log.warn(s"failed to transform: ${t}",e)
            Seq[O]()
        }
      })
      .via(counterO)
      .log(ingestFlowName()).withAttributes(logLevels)
    
    // Create BroadcastHub - this materializes and runs the flow
    // Connect to this flow's sink AND expose as Source for downstream flows
    val s0 = sink()
    val s1 = if(retrySettings.isDefined) {
      RestartSink.withBackoff(retrySettings.get) { () =>
        log.info(s"sink retry: ${retrySettings.get}")
        s0
      } 
    } else {
      s0
    }
    
    // Connect to both the actual sink and BroadcastHub
    // BroadcastHub.sink() returns a Sink, and toMat(Keep.right) gives us the Source
    val broadcastSource = outputFlow
      .alsoTo(sink0()) // Always connect to sink0 (ignore sink)
      .alsoTo(s1)      // Connect to actual sink
      .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)
      .run()
    
    log.info(s"Flow started via BroadcastHub: ${ingestFlowName()}")
    broadcastSource
  }
}


object IngestFlow {
  implicit final class FlowThrottled[T, Mat](private val flow: Flow[T, T, Mat]) extends AnyVal {
    def throttled(elements: Int, interval: FiniteDuration): Flow[T, T, Mat] =
      if (interval.toMillis == 0) flow else flow.throttle(elements, interval)
  }
}
