package io.syspulse.skel.ai.provider.gemini

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

import io.syspulse.skel.ai.core.GeminiURI
import io.syspulse.skel.ai.provider.openai.OpenAiLike
import io.syspulse.skel.ai.core.AiURI

class GeminiAi(uri:GeminiURI) extends OpenAiLike(uri)
