package io.syspulse.skel.wf.runtime.thread

import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class RunningThread(link:Linking) extends Running {
  val log = Logger(s"${this}")
  
  @volatile
  var terminated = false
  val queue = new ArrayBlockingQueue[ExecEvent](5)

  val thr = new Thread() {
    override def run() = {
      log.info(s"queue=${queue}: link=${link}: running...")
      while( !terminated ) {
        val e = queue.take
        link.output(e)
      }
      log.info(s"queue=${queue}: link=${link}: stop")
    }
  }

  override def !(e: ExecEvent) = {
    queue.put(e)
  }

  def start() = {
    thr.start()
  }

  def stop() = {
    terminated = true
    queue.put(ExecCmdStop())    
  }
}

class RuntimeThreads extends Runtime {
  val log = Logger(s"${this}")

  def spawn(link: Linking):Running = {
    new RunningThread(link)
  }
}

