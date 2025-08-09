package io.syspulse.skel.ingest.flow

import scala.util.Random
import akka.stream.scaladsl.{Sink, Source}
import akka.NotUsed
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicReference
import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import java.util.concurrent.TimeUnit
import akka.stream.Attributes
import akka.util.ByteString

case class Cursor(last: Long)

trait ExternalService {
  def getLatest(): Long
}

class ExternalServiceRand(speed: String) extends ExternalService {
  @volatile
  var last = 0L
  override def getLatest(): Long = {
    val next = speed.split(":").filter(_.nonEmpty).toList match {
      case ("rand" | "random") :: Nil => Random.nextInt(3)
      case ("rand" | "random") :: v :: Nil => Random.nextInt(v.toInt)
      case (speed :: Nil) => speed.toInt
      case _ => 3
    }    
    last = last + next
    Console.err.println(s"ExternalServiceRand: >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> ${last}")    
    last
  }
}

class CursorBasedProcessor(
  maxBatchSize: Int,
  pollInterval: FiniteDuration,
  work: Long => Unit,
  throttle: Long,
  externalService: ExternalService,
  system: ActorSystem
)(implicit ec: ExecutionContext) {
  
  private val cursor = new AtomicReference[Cursor](Cursor(0L))
  
  // RACE !
  def createSource1(): Source[Long, NotUsed] = {
    Source
      .tick(Duration.Zero, pollInterval, ())
      .mapMaterializedValue(_ => NotUsed)
      .buffer(1, OverflowStrategy.backpressure)      
      .mapAsync(parallelism = 1) { _ =>
        val latest = externalService.getLatest()
        val current = cursor.get().last
        
        val range = current to latest
        Console.err.println(s"Cursor: current=${current}, latest=$latest, range = ${range}")
        Future.successful(range)        
      }
      .filter { range => range.size > 0 } // Filter out "no work" cases
      .mapConcat { range =>
        // Create all batches from the range        
        val batches = range.grouped(maxBatchSize)        
        batches
      }
      .mapConcat(batch => {
        Console.err.println(s"Batch = ${batch}")
        batch
      })
      .map(b => b)
      
  }

  // WORKING !
  def createSource2(): Source[Long, NotUsed] = {
    Source
      .tick(Duration.Zero, pollInterval, ())
      .mapMaterializedValue(_ => NotUsed)
            
      .map { _ =>
        val latest = externalService.getLatest()
        val last = cursor.get().last
        val current = last + 1
        
        val range0 = current to latest
        val range = range0.take(maxBatchSize)
        Console.err.println(s"Cursor: last=${last}, current=${current}, latest=$latest, range = ${range}")
        range
      }
      .mapConcat(range => {
        Console.err.println(s"Range = ${range}")
        range
      })
      .map(b => b)
  }

  // RACE !
  def createSource3(): Source[Long, NotUsed] = {
    Console.err.println(s"createSource3")
    
    Source
      .tick(Duration.Zero, pollInterval, ())
      .mapMaterializedValue(_ => NotUsed)
            
      .mapAsync(parallelism = 1) { _ =>
        val latest = externalService.getLatest()
        val last = cursor.get().last
        val current = last + 1
        
        val range0 = current to latest
        val range = range0.take(maxBatchSize)
        Console.err.println(s"Cursor: last=${last}, current=${current}, latest=$latest, range = ${range}")
        Future.successful(range)
      }
      .mapConcat(range => {
        Console.err.println(s"Range = ${range}")
        range
      })
      .map(b => b)
  }

  // WORKING !
  def createSource4(): Source[Long, NotUsed] = {
    Console.err.println(s"createSource4")

    Source
      .tick(Duration.Zero, pollInterval, ())
      .mapMaterializedValue(_ => NotUsed)
            
      .map { _ =>
        val latest = externalService.getLatest()
        val last = cursor.get().last
        val current = last + 1
        
        val range0 = current to latest
        val range = range0.take(maxBatchSize)
        Console.err.println(s"Cursor: last=${last}, current=${current}, latest=$latest, range = ${range}")
        range
      }
      .mapConcat(range => {
        Console.err.println(s"Range = ${range}")
        range
      })
      .throttle(1, FiniteDuration(throttle,TimeUnit.MILLISECONDS))
      .map(b => b)
  }

  // RACE !
  def createSource5(): Source[Long, NotUsed] = {
    Console.err.println(s"createSource5")

    Source
      .tick(Duration.Zero, pollInterval, ())
      .mapMaterializedValue(_ => NotUsed)
            
      .map { _ =>
        val latest = externalService.getLatest()
        val last = cursor.get().last
        val current = last + 1
        
        val range0 = current to latest
        val range = range0.take(maxBatchSize)
        Console.err.println(s"Cursor: last=${last}, current=${current}, latest=$latest, range = ${range}")
        range
      }
      // WARNING: This filter is the only difference between createSource4 and createSource5 !
      .filter { range => range.size > 0 } 
      .mapConcat(range => {
        Console.err.println(s"Range = ${range}")
        range
      })
      .throttle(1, FiniteDuration(throttle,TimeUnit.MILLISECONDS))
      .map(b => b)
  }

  // WORKING !
  def createSource6(blockMax: Long): Source[Long, NotUsed] = {
    Console.err.println(s"createSource6(${blockMax})")

    Source
      .tick(Duration.Zero, pollInterval, ())
      .mapMaterializedValue(_ => NotUsed)
            
      .map { _ =>
        val latest = externalService.getLatest()
        val last = cursor.get().last
        val current = last + 1
        
        val range0 = current to latest
        val range = range0.take(maxBatchSize)
        Console.err.println(s"Cursor: last=${last}, current=${current}, latest=$latest, range = ${range}")
        range
      }
      .mapConcat(range => {
        Console.err.println(s"Range = ${range}")
        range
      })
      .takeWhile(b => b < blockMax)
      .throttle(1, FiniteDuration(throttle,TimeUnit.MILLISECONDS))
      .map(b => b)
  }

  // RACE !
  def createSource7(blockMax: Long): Source[Long, NotUsed] = {
    Console.err.println(s"createSource7(${blockMax})")

    Source
      .tick(Duration.Zero, pollInterval, ())
      .mapMaterializedValue(_ => NotUsed)
            
      .map { _ =>
        val latest = externalService.getLatest()
        val last = cursor.get().last
        val current = last + 1
        
        val range0 = current to latest
        val range = range0.take(maxBatchSize)
        Console.err.println(s"Cursor: last=${last}, current=${current}, latest=$latest, range = ${range}")
        range
      }
      // WARNING: Rece is only because of this filter !
      .filter { range => range.size > 0 }
      .mapConcat(range => {
        Console.err.println(s"Range = ${range}")
        range
      })      
      .takeWhile(b => b < blockMax)
      .throttle(1, FiniteDuration(throttle,TimeUnit.MILLISECONDS))      
      .map(b => b)      
  }

  // RACE !
  def createSource8(blockMax: Long): Source[Long, NotUsed] = {
    Console.err.println(s"createSource8(${blockMax})")

    Source
      .tick(Duration.Zero, pollInterval, ())
      
      .mapMaterializedValue(_ => NotUsed)
      
      .mapConcat { _ =>
        val latest = externalService.getLatest()
        val last = cursor.get().last
        val current = last + 1
        
        val range0:List[Long] = Seq.range(current,latest).toList
        val range = range0.take(maxBatchSize)
        Console.err.println(s"Cursor: last=${last}, current=${current}, latest=$latest, range = ${range}")
        range
      }      
      // WARNING: Race is only because of this filter !
      .filter { b => b > 0 }
      // .mapConcat{ range:List[Long] => {
      //   Console.err.println(s"Range = ${range}")
      //   range
      // }}
      .takeWhile(b => b < blockMax)
      .throttle(1, FiniteDuration(throttle,TimeUnit.MILLISECONDS))      
      .map(b => b)      
  }
  
  // =========================================================================================================================
  def createSink(): Sink[Long, Future[akka.Done]] = {
    Sink.foreachAsync[Long](parallelism = 1) { block =>
      Future {
        //Console.err.println(s"Process: Block[${block}]")
        val ts0 = System.currentTimeMillis()
        work(block)
        val ts1 = System.currentTimeMillis()
        val duration = ts1 - ts0
        
        cursor.set(Cursor(block))
        
        Console.err.println(s"Process: Block[${block}] ${duration}ms (${cursor})")
      }
    }
  }
}

object AppTest1 extends App {
  import akka.actor.ActorSystem
  import akka.stream.scaladsl.RunnableGraph
  import scala.concurrent.duration._
  
  // Parse command line arguments
  val argsMap = args.sliding(2, 2).map {
    case Array(key, value) => key -> value
  }.toMap
  
  val batchSize = argsMap.getOrElse("--batch.size", "5").toInt
  val work = argsMap.getOrElse("--work", "1000").toInt
  val pollInterval = argsMap.getOrElse("--poll.interval", "150").toInt
  val runDuration = argsMap.getOrElse("--run.duration", "60000").toInt
  val speed = argsMap.getOrElse("--speed", "3")
  val sourceType = argsMap.getOrElse("--source", "1")
  val throttle = argsMap.getOrElse("--throttle", "100").toInt
  val blockMax = argsMap.getOrElse("--block.max", "50").toLong
  
  println(s"Configuration:")
  println(s"  Batch size: $batchSize")
  println(s"  Work: ${work}ms")
  println(s"  Poll interval: ${pollInterval}ms")
  println(s"  Run duration: ${runDuration}ms")
  println(s"  Speed: ${speed}")
  println(s"  Source type: ${sourceType}")
  println(s"  Throttle: ${throttle}ms")
  println(s"  Block max: ${blockMax}")
  
  implicit val system = ActorSystem("AppTest1")
  implicit val ec = system.dispatcher
  
  val externalService = new ExternalServiceRand(speed)
  
  def work(id: Long): Unit = {
    Thread.sleep(work) // Simulate work
  }
  
  val processor = new CursorBasedProcessor(
    maxBatchSize = batchSize,
    pollInterval = pollInterval.millis,
    work = work,
    throttle = throttle,
    externalService = externalService,
    system = system
  )

  val source = sourceType match {
    case "1" => processor.createSource1()
    case "2" => processor.createSource2()
    case "3" => processor.createSource3()
    case "4" => processor.createSource4()
    case "5" => processor.createSource5()
    case "6" => processor.createSource6(blockMax)
    case "7" => processor.createSource7(blockMax)
    case "8" => processor.createSource8(blockMax)
    case _ => processor.createSource1()
  }
  
  // val graph = RunnableGraph.fromGraph(
  //   source
  //     .log("flow")
  //     .addAttributes(
  //       Attributes.logLevels(
  //         onElement = Attributes.LogLevels.Off,
  //         onFinish = Attributes.LogLevels.Warning,
  //         onFailure = Attributes.LogLevels.Error))
  //     .to(processor.createSink())
  // )
  // graph.run()

  val flow = source
    .log("flow")
    .map { element =>
      // Add any additional processing logic here
      element
    }
    .addAttributes(
      Attributes.logLevels(
        onElement = Attributes.LogLevels.Off,
        onFinish = Attributes.LogLevels.Warning,
        onFailure = Attributes.LogLevels.Error)) 
    .runWith(processor.createSink())

  Console.err.println(s"flow = ${flow}")
  
  // Run for specified duration then shutdown
  system.scheduler.scheduleOnce(runDuration.millis) {
    Console.err.println("Shutting down...")
    system.terminate()
  }
} 

