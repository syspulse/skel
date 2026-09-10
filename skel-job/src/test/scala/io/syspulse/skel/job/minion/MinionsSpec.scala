package io.syspulse.skel.job.minion

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Await
import scala.concurrent.duration._

class MinionsSpec extends AnyWordSpec with Matchers {

  // Generous waits so slow CI / GC does not flake lifecycle tests.
  val WaitMs = 8000L
  val PollMs = 50L
  val Freq = "250"

  def waitUntil(cond: => Boolean, timeoutMs: Long = WaitMs, stepMs: Long = PollMs): Boolean = {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!cond && System.currentTimeMillis() < deadline) Thread.sleep(stepMs)
    cond
  }

  def awaitLatch(l: CountDownLatch, timeoutMs: Long = WaitMs): Boolean =
    l.await(timeoutMs, TimeUnit.MILLISECONDS)

  class RecMinion(val id: String, delayMs: Long = 0L) extends Minion[String, String] {
    val runs = new AtomicInteger(0)
    val oks = new AtomicInteger(0)
    val errs = new AtomicInteger(0)
    @volatile var last: Option[String] = None
    def run(): String = {
      runs.incrementAndGet()
      if (delayMs > 0) Thread.sleep(delayMs)
      id
    }
    def onSuccess(r: String): Unit = { last = Some(r); oks.incrementAndGet() }
    def onError(e: Throwable): Unit = { errs.incrementAndGet() }
  }

  class SleepMinion(sleepMs: Long = 30000L) extends Minion[String, String] {
    val began = new CountDownLatch(1)
    val ended = new CountDownLatch(1)
    val oks = new AtomicInteger(0)
    val errs = new AtomicInteger(0)
    @volatile var interrupted = false
    @volatile var finishedOk = false
    def run(): String = {
      began.countDown()
      try {
        Thread.sleep(sleepMs)
        finishedOk = true
        "done"
      } catch {
        case _: InterruptedException =>
          interrupted = true
          throw new RuntimeException("interrupted")
      } finally {
        ended.countDown()
      }
    }
    def onSuccess(r: String): Unit = oks.incrementAndGet()
    def onError(e: Throwable): Unit = errs.incrementAndGet()
  }

  def master(max: Long = 10L, threads: Int = 2): JobMaster[String, String] =
    new JobMaster[String, String](freq = Freq, threads = threads, max = max)

  "JobMaster state" should {

    "be IDLE then STARTED / STOPPED / TERMINATED" in {
      val m = master()
      try {
        m.state shouldBe JobMaster.State.IDLE

        m.start()
        m.state shouldBe JobMaster.State.STARTED

        m.stop()
        m.state shouldBe JobMaster.State.STOPPED
        m.queue shouldBe empty

        m.start()
        m.state shouldBe JobMaster.State.STARTED

        m.terminate()
        m.state shouldBe JobMaster.State.TERMINATED
      } finally {
        try m.terminate() catch { case _: Exception => () }
      }
    }

    "process a job after start (onSuccess)" in {
      val m = master()
      val job = new RecMinion("a")
      try {
        m.start()
        Await.result(m + job, 5.seconds) shouldBe "a"
        waitUntil(job.oks.get() == 1) shouldBe true
        job.last shouldBe Some("a")
        job.errs.get() shouldBe 0
      } finally m.terminate()
    }
  }

  "JobMaster multiple minions" should {

    "run several jobs and callback each onSuccess" in {
      val m = master(threads = 2)
      val jobs = (1 to 5).map(i => new RecMinion(s"m$i", delayMs = 20L))
      try {
        m.start()
        val fs = jobs.map(j => m + j)
        fs.map(f => Await.result(f, 8.seconds)).toSet shouldBe jobs.map(_.id).toSet
        waitUntil(jobs.forall(_.oks.get() == 1)) shouldBe true
        jobs.foreach { j =>
          j.runs.get() shouldBe 1
          j.errs.get() shouldBe 0
        }
        waitUntil(m.queue.isEmpty) shouldBe true
      } finally m.terminate()
    }
  }

  "JobMaster queue limit" should {

    "reject overflow beyond max" in {
      val m = master(max = 2, threads = 1)
      val a = new SleepMinion()
      val b = new SleepMinion()
      val c = new RecMinion("overflow")
      try {
        m.start()
        val fa = m + a
        val fb = m + b
        fa.isCompleted shouldBe false
        fb.isCompleted shouldBe false
        m.queue.size shouldBe 2

        val fc = m + c
        Await.ready(fc, 2.seconds)
        fc.value.get.isFailure shouldBe true
        fc.value.get.failed.get.getMessage should include("Job queue full")
        c.runs.get() shouldBe 0
      } finally m.terminate()
    }
  }

  "JobMaster stop / terminate" should {

    "stop: reject new jobs and interrupt old ones (no onSuccess)" in {
      val m = master(threads = 1)
      val job = new SleepMinion()
      try {
        m.start()
        m + job
        awaitLatch(job.began) shouldBe true

        m.stop()
        m.state shouldBe JobMaster.State.STOPPED
        m.queue shouldBe empty
        awaitLatch(job.ended) shouldBe true
        job.interrupted shouldBe true
        job.finishedOk shouldBe false
        job.oks.get() shouldBe 0

        val extra = new RecMinion("after-stop")
        val f = m + extra
        Await.ready(f, 2.seconds)
        f.value.get.isFailure shouldBe true
        f.value.get.failed.get.getMessage should include("cannot run jobs")
        extra.runs.get() shouldBe 0
      } finally m.terminate()
    }

    "terminate: reject new jobs, interrupt old ones, start() does not revive" in {
      val m = master(threads = 1)
      val job = new SleepMinion()
      try {
        m.start()
        m + job
        awaitLatch(job.began) shouldBe true

        m.terminate()
        m.state shouldBe JobMaster.State.TERMINATED
        m.queue shouldBe empty
        awaitLatch(job.ended) shouldBe true
        job.interrupted shouldBe true
        job.finishedOk shouldBe false
        job.oks.get() shouldBe 0

        m.start()
        m.state shouldBe JobMaster.State.TERMINATED

        val extra = new RecMinion("after-term")
        val f = m + extra
        Await.ready(f, 2.seconds)
        f.value.get.isFailure shouldBe true
        f.value.get.failed.get.getMessage should include("cannot run jobs")
        extra.runs.get() shouldBe 0
      } finally {
        try m.terminate() catch { case _: Exception => () }
      }
    }
  }
}
