package io.syspulse.skel.enroll

import io.jvm.uuid._
import scala.util.Random

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.DoNotDiscover
import org.scalatest.Sequential
import akka.persistence.journal.PersistencePluginProxy

import io.syspulse.skel.enroll.event._

object TestsRecovery {
  val id1 = UUID.randomUUID()
  
  val config = """
akka {

  extensions = [akka.persistence.Persistence]

  persistence {

    journal {
      plugin = "akka.persistence.journal.leveldb"
      auto-start-journals = ["akka.persistence.journal.leveldb"]
      leveldb.dir = "target/journal"
    }

    snapshot-store {
      plugin = "akka.persistence.snapshot-store.local"
      auto-start-snapshot-stores = ["akka.persistence.snapshot-store.local"]
    }

  }

}
akka.actor.allow-java-serialization = on
  """
}

class EnrollEventRecoverySpec extends Sequential(
   new EnrollEventSpec1,
   new EnrollEventSpec2
)

class EnrollEventActorSystem extends ScalaTestWithActorTestKit(TestsRecovery.config)

@DoNotDiscover
class EnrollEventSpec2 extends AnyWordSpecLike {

  "EnrollEvent-2" should {

    "recover email as Events playback in ActorSystem-2 from LevelDB journal" in {
      val as = new EnrollEventActorSystem

      val id1 = TestsRecovery.id1

      val e2 = Enroll(id1)      
      val a2 = as.testKit.spawn(e2)
      val probe2 = as.testKit.createTestProbe[Enroll.Summary]
      a2 ! Enroll.Get(probe2.ref)
      val r2 = probe2.receiveMessage()
      info(s"r2 = ${r2}")

      as.testKit.internalSystem.terminate()
    }

  }
}

@DoNotDiscover
class EnrollEventSpec1 extends AnyWordSpecLike {

  "EnrollEvent-1" should {

    "recover email as Events playback in ActorSystem-1" in {
      val as = new EnrollEventActorSystem

      val id1 = TestsRecovery.id1

      val e1 = Enroll(id1)      
      val a1 = as.testKit.spawn(e1)
      val probe = as.testKit.createTestProbe[StatusReply[Enroll.Summary]]      
      a1 ! Enroll.AddEmail("user-1@email.com", probe.ref)
      val r = probe.receiveMessage()
      
      val probe1 = as.testKit.createTestProbe[Enroll.Summary]
      a1 ! Enroll.Get(probe1.ref)
      val r1 = probe1.receiveMessage()
      info(s"r1 = ${r1}")

      as.testKit.stop(a1)

      val e2 = Enroll(id1)      
      val a2 = as.testKit.spawn(e2)
      val probe2 = as.testKit.createTestProbe[Enroll.Summary]
      a2 ! Enroll.Get(probe2.ref)
      val r2 = probe2.receiveMessage()
      info(s"r2 = ${r2}")

      as.testKit.internalSystem.terminate()
    }
  }
}
