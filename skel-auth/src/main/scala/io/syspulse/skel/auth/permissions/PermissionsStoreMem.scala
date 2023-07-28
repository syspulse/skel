package io.syspulse.skel.auth.permissions

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class PermissionsStoreMem extends PermissionsStoreCache
