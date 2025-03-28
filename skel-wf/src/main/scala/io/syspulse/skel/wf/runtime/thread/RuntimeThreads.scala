package io.syspulse.skel.wf.runtime.thread

import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class RunningThread(link:Linking,queueSize:Int = 1999) extends Running {
  val log = Logger(s"${this}")
  
  @volatile
  var terminated = false
  // ATTENTION: Queue size is important for SeqExec
  val queue = new ArrayBlockingQueue[ExecEvent](queueSize)

  val thr = new Thread() {
    override def run() = {
      log.debug(s"queue=${queue}: link=${link}: running...")
      while( !terminated ) {
        val e = queue.take
        link.output(e)
      }
      log.debug(s"queue=${queue}: link=${link}: stopped")
    }
  }

  override def !(e: ExecEvent):Try[RunningThread] = {
    try {
      queue.put(e)
      Success(this)
    } catch {
      case e:Exception => Failure(e)
    }
  }

  def start():Try[RunningThread] = {
    thr.start()
    Success(this)
  }

  def stop():Try[RunningThread] = {
    terminated = true
    queue.put(ExecCmdStop(this.toString))
    Success(this)
  }
}

// ---------------------------------- Runtime ---
class RuntimeThreads extends Runtime {
  val log = Logger(s"${this}")

  def spawn(link: Linking):Try[Running] = {
    Success(new RunningThread(link))
  }
}

