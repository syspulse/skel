package io.syspulse.skel.user

import scala.collection.immutable

import io.jvm.uuid._

final case class User(
  id:UUID, 
  email:String, 

  name:Option[String] = None, 
  xid:Option[String] = None,     // external ID (e.g. wallet address)
  avatar:Option[String] = None,  // avatar URL

  ts0:Long = System.currentTimeMillis(), // timestamp of creation
  ts:Long = System.currentTimeMillis(),  // timestamp of last update

  meta: Option[Map[String, Any]] = None, // arbitrary metadata
)
