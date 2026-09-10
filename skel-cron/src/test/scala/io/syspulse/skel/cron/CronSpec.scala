package io.syspulse.skel.cron

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.{Try,Success,Failure}
import java.time._
import java.util.concurrent.atomic.AtomicInteger
import io.syspulse.skel.util.TimeUtil
import io.syspulse.skel.config.{Configuration, ConfigurationMap}
// import io.syspulse.skel.util.Util

class CronSpec extends AnyWordSpec with Matchers {

  // Generous windows so GC / slow CI does not flake start/stop/terminate.
  // First tick: wait up to 8s. After stop/terminate: wait > 2 intervals with no extra ticks.
  val WaitFirstTick = 8000L
  val WaitStopped = 3500L
  val FreqInterval = "1 second"
  val FreqDelayMs = 200L
  val QuartzEverySec = "*/1 * * * * ?"

  def waitUntil(cond: => Boolean, timeoutMs: Long, stepMs: Long = 50L): Boolean = {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!cond && System.currentTimeMillis() < deadline) Thread.sleep(stepMs)
    cond
  }

  def assertStable(n: => Int, waitMs: Long, stepMs: Long = 100L): Unit = {
    val snap = n
    val deadline = System.currentTimeMillis() + waitMs
    while (System.currentTimeMillis() < deadline) {
      Thread.sleep(stepMs)
      n shouldBe snap
    }
  }

  private val quartzSeq = new AtomicInteger(0)

  def isolatedQuartz(exec: Long => Boolean, expr: String): CronQuartz = {
    val name = s"cron-spec-${quartzSeq.incrementAndGet()}-${System.nanoTime}"
    val cfg = new ConfigurationMap()
    cfg + (s"$name.org.quartz.scheduler.instanceName", name)
    cfg + (s"$name.org.quartz.scheduler.skipUpdateCheck", "true")
    cfg + (s"$name.org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool")
    cfg + (s"$name.org.quartz.threadPool.threadCount", "1")
    cfg + (s"$name.org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore")
    new CronQuartz(
      exec, expr,
      conf = Some((name, new Configuration(Seq(cfg)))),
      cronName = s"cron-$name",
      jobName = s"job-$name",
      groupName = s"group-$name",
    )
  }

  def safeTerminate(c: Cron[_]): Unit =
    try c.terminate() catch { case _: Exception => () }
    
  "CronQuartz" should {

    "NOT schedule events for: '0 0/5 * * * ?'" in {
      @volatile var n = 0
      val c = new CronQuartz((elaped:Long) => {
          n = n + 1
          true
        },
        "0 0/5 * * * ?"
      )
      val r = c.start()
      r.getClass should !== (classOf[Failure[_]])
      
      Thread.sleep(1000L)
      c.stop()

      n should === (0)
    }

    "schedule events for: '*/1 * * * * ?'" in {
      @volatile var n = 0
      val c = new CronQuartz((elaped:Long) => {
          n = n + 1
          true
        },
        "*/1 * * * * ?"
      )
      val r = c.start()
      r.getClass should !== (classOf[Failure[_]])

      waitUntil(n >= 1, WaitFirstTick) shouldBe true
      c.stop()
      n should be >= 1
    }

    "start firing, stop with no further ticks, and start again" in {
      @volatile var n = 0
      val c = isolatedQuartz(_ => { n += 1; true }, QuartzEverySec)
      try {
        c.start().isSuccess shouldBe true
        waitUntil(n >= 1, WaitFirstTick) shouldBe true

        c.stop()
        Thread.sleep(150)
        assertStable(n, WaitStopped)

        val afterStop = n
        c.start().isSuccess shouldBe true
        waitUntil(n > afterStop, WaitFirstTick) shouldBe true
      } finally safeTerminate(c)
    }

    "terminate stops ticks and start() fails afterwards" in {
      @volatile var n = 0
      val c = isolatedQuartz(_ => { n += 1; true }, QuartzEverySec)
      try {
        c.start().isSuccess shouldBe true
        waitUntil(n >= 1, WaitFirstTick) shouldBe true

        c.terminate()
        Thread.sleep(150)
        assertStable(n, WaitStopped)

        c.start().isFailure shouldBe true
      } finally safeTerminate(c)
    }

    "stop then terminate: start() still fails" in {
      @volatile var n = 0
      val c = isolatedQuartz(_ => { n += 1; true }, QuartzEverySec)
      try {
        c.start().isSuccess shouldBe true
        waitUntil(n >= 1, WaitFirstTick) shouldBe true
        c.stop()
        Thread.sleep(150)
        assertStable(n, WaitStopped)

        c.terminate()
        c.start().isFailure shouldBe true
      } finally safeTerminate(c)
    }

    "interval '*/1 * * * * ?' == 1000" in {
      val i = CronQuartz.toMillis("*/1 * * * * ?")
      i should === (1000L)
    }

    "interval '0 */2 * ? * *' == 120000" in {
      val i = CronQuartz.toMillis("0 */2 * ? * *")
      i should === (120000L)
    }
  }
  
  "CronFreq" should {

    "parse only milliseconds correctly" in {
      TimeUtil.humanToMillis("100") shouldBe 100L
      TimeUtil.humanToMillis("1") shouldBe 1L
      TimeUtil.humanToMillis("500") shouldBe 500L
    }

    "parse milliseconds correctly" in {
      TimeUtil.humanToMillis("100 ms") shouldBe 100L
      TimeUtil.humanToMillis("1 millisecond") shouldBe 1L
      TimeUtil.humanToMillis("500 milliseconds") shouldBe 500L
    }

    "parse seconds correctly" in {
      TimeUtil.humanToMillis("1 sec") shouldBe 1000L
      TimeUtil.humanToMillis("10 second") shouldBe 10000L
      TimeUtil.humanToMillis("5 seconds") shouldBe 5000L
    }

    "parse minutes correctly" in {
      TimeUtil.humanToMillis("1 min") shouldBe 60000L
      TimeUtil.humanToMillis("2 minute") shouldBe 120000L
      TimeUtil.humanToMillis("3 minutes") shouldBe 180000L
    }

    "parse hours correctly" in {
      TimeUtil.humanToMillis("1 hour") shouldBe 3600000L
      TimeUtil.humanToMillis("2 hours") shouldBe 7200000L
    }

    "parse days correctly" in {
      TimeUtil.humanToMillis("1 day") shouldBe 86400000L
      TimeUtil.humanToMillis("2 days") shouldBe 172800000L
    }

    "be case-insensitive" in {
      TimeUtil.humanToMillis("1 MIN") shouldBe 60000L
      TimeUtil.humanToMillis("1 Sec") shouldBe 1000L
    }

    "allow spaces between number and unit" in {
      TimeUtil.humanToMillis("1    min") shouldBe 60000L
      TimeUtil.humanToMillis("10   seconds") shouldBe 10000L
    }

    "throw IllegalArgumentException for invalid formats" in {
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("-1m")
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("minute")
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("1 mAnth")
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("1.5 Zours")
    }

    "start firing, stop with no further ticks, and start again" in {
      @volatile var n = 0
      val c = new CronFreq(_ => { n += 1; true }, FreqInterval, delay0 = FreqDelayMs)
      try {
        c.start().isSuccess shouldBe true
        waitUntil(n >= 1, WaitFirstTick) shouldBe true

        c.stop()
        Thread.sleep(150)
        assertStable(n, WaitStopped)

        val afterStop = n
        c.start().isSuccess shouldBe true
        waitUntil(n > afterStop, WaitFirstTick) shouldBe true
      } finally safeTerminate(c)
    }

    "terminate stops ticks and start() fails afterwards" in {
      @volatile var n = 0
      val c = new CronFreq(_ => { n += 1; true }, FreqInterval, delay0 = FreqDelayMs)
      try {
        c.start().isSuccess shouldBe true
        waitUntil(n >= 1, WaitFirstTick) shouldBe true

        c.terminate()
        Thread.sleep(150)
        assertStable(n, WaitStopped)

        Try(c.start()).isFailure shouldBe true
      } finally safeTerminate(c)
    }

    "stop then terminate: start() still fails" in {
      @volatile var n = 0
      val c = new CronFreq(_ => { n += 1; true }, FreqInterval, delay0 = FreqDelayMs)
      try {
        c.start().isSuccess shouldBe true
        waitUntil(n >= 1, WaitFirstTick) shouldBe true
        c.stop()
        Thread.sleep(150)
        assertStable(n, WaitStopped)

        c.terminate()
        Try(c.start()).isFailure shouldBe true
      } finally safeTerminate(c)
    }
    
  }

  "Cron" should {

    "limit 100msec to 1sec" in {
      @volatile var n = 0
      val c = Cron((elaped:Long) => {
          n = n + 1
          true
        },
        "100msec",
        rateLimit = Some(1000L)
      )
      val r = c.start()
      Thread.sleep(1250L)
      c.stop()

      // should be 2 since it fires extra one immediately
      n should === (2)      
    }

    "limit 1sec to 2sec" in {
      @volatile var n = 0
      val c = Cron((elaped:Long) => {
          n = n + 1
          true
        },
        "*/1 * * * * ?",
        rateLimit = Some(2000L)
      )
      val r = c.start()
      
      Thread.sleep(1250L)
      n should === (0)
      
      Thread.sleep(1250L)      
      c.stop()      
      n should === (1)
    }
  }
}

