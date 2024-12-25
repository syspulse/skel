package io.syspulse.skel.twitter

import scala.jdk.CollectionConverters._

case class Twit(
  id:String,
  author_id:String,
  author_name:String,
  text:String,
  created_at:Long
)
