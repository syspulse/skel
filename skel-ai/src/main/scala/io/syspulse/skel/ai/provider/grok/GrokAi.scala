package io.syspulse.skel.ai.provider.grok

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import com.typesafe.scalalogging.Logger

import os._
import io.jvm.uuid._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.ai.core.GrokURI
import io.syspulse.skel.ai.provider.openai.OpenAiLike
import io.syspulse.skel.ai.core.AiURI

class GrokAi(uri:GrokURI) extends OpenAiLike(uri)