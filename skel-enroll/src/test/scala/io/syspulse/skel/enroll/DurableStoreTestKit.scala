package io.syspulse.skel.enroll

import io.jvm.uuid._
import scala.util.Random

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import org.scalatest.wordspec.AnyWordSpecLike

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto._

import io.syspulse.skel.enroll.flow.state._
import scala.concurrent.Future
import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils
import akka.Done

abstract class DurableStoreTestKit(config:String = 
"""
include "general.conf"

akka.actor.allow-java-serialization = on

akka {
  persistence {
    journal {
      plugin = "jdbc-journal"
      // Enable the line below to automatically start the journal when the actorsystem is started
      // auto-start-journals = ["jdbc-journal"]
    }
    snapshot-store {
      plugin = "jdbc-snapshot-store"
      // Enable the line below to automatically start the snapshot-store when the actorsystem is started
      // auto-start-snapshot-stores = ["jdbc-snapshot-store"]
    }
    state {
      # Absolute path to the KeyValueStore plugin configuration entry used by
      # DurableStateBehavior actors by default.
      # DurableStateBehavior can override `durableStateStorePluginId` method (`withDurableStateStorePluginId`)
      # in order to rely on a different plugin.
      plugin = "jdbc-durable-state-store"
      auto-start-durable-state-store = ["jdbc-snapshot-store"]
    }  
  }
}

jdbc-journal {
  slick {
    profile = "slick.jdbc.H2Profile$"
    db {
      url = "jdbc:h2:mem:test-database;DATABASE_TO_UPPER=false;"
      user = "root"
      password = "root"
      driver = "org.h2.Driver"
      numThreads = 5
      maxConnections = 5
      minConnections = 1
    }
  }
  dao = "akka.persistence.jdbc.journal.dao.DefaultJournalDao"
}

# the akka-persistence-snapshot-store in use
jdbc-snapshot-store {
  slick {
    profile = "slick.jdbc.H2Profile$"
    db {
      url = "jdbc:h2:mem:test-database;DATABASE_TO_UPPER=false;"
      user = "root"
      password = "root"
      driver = "org.h2.Driver"
      numThreads = 5
      maxConnections = 5
      minConnections = 1
    }
  }
  dao = "akka.persistence.jdbc.snapshot.dao.DefaultSnapshotDao"
}

# the akka-persistence-query provider in use
jdbc-read-journal {
  slick {
    profile = "slick.jdbc.H2Profile$"
    db {
      url = "jdbc:h2:mem:test-database;DATABASE_TO_UPPER=false;"
      user = "root"
      password = "root"
      driver = "org.h2.Driver"
      numThreads = 5
      maxConnections = 5
      minConnections = 1
    }
  }
}

# the akka-persistence-jdbc provider in use for durable state store
jdbc-durable-state-store {
  slick {
    profile = "slick.jdbc.H2Profile$"
    db {
      url = "jdbc:h2:mem:test-database;DATABASE_TO_UPPER=false;"
      user = "root"
      password = "root"
      driver = "org.h2.Driver"
      numThreads = 5
      maxConnections = 5
      minConnections = 1
    }
  }
  dao = "akka.persistence.jdbc.durable.dao.DefaultDurableDao"
}

slick {
  profile = "slick.jdbc.H2Profile$"
  db {
    url = "jdbc:h2:mem:test-database;DATABASE_TO_UPPER=false;"
    user = "root"
    password = "root"
    driver = "org.h2.Driver"
    numThreads = 5
    maxConnections = 5
    minConnections = 1
  }
}
""") extends ScalaTestWithActorTestKit(config) {

  def getConfigDurable() = config

  val done: Future[Done] = SchemaUtils.createIfNotExists("jdbc-durable-state-store")
}

