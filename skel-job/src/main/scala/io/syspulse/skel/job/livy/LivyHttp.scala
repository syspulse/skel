package io.syspulse.skel.job.livy

import scala.util.Random

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import akka.actor.typed.ActorSystem
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.Future
import scala.util.{ Try, Failure, Success }

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.util.Util

import io.jvm.uuid._
import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.util.ByteString
import akka.actor
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

import io.syspulse.skel.job._
import io.syspulse.skel.job.livy._

object LivyHttp {
  implicit val as:actor.ActorSystem = actor.ActorSystem("LivyHttp-System")
  implicit val ec = as.getDispatcher
}

class LivyHttp(uri:String)(timeout:Long) extends JobEngine {
  val log = Logger(s"${this}")
  
  import LivyHttp._
  import LivyJson._

  override def toString = s"${this.getClass.getSimpleName}(${request})"

  case class Request(uri:String,verb:HttpMethod,headers:Map[String,String]=Map(),body:Option[String] = None,async:Boolean=false) {
    def getHeaders:Seq[HttpHeader] = headers.map{ case(k,v) => RawHeader(k,v) }.toSeq 

    // def withId(subj:String,msg:String) = {
    //   this.copy(uri = uri
    //     .replaceAll("\\{subj\\}",subj)
    //     .replaceAll("\\{msg\\}",msg)
    //   )
    // }
  }

  // http://POST/host:port/url{subj}/{msg}
  // replace {} with real data
  def parseUri(uri:String) = {

    def buildRequest(proto:String,verb:HttpMethod,rest:List[String],headers:Map[String,String]=Map(),body:Option[String]=None,async:Boolean=false) = {
      Request(s"${proto}://${rest.mkString("/")}",verb, headers, body)
    }

    uri.split("(://|/)").toList match {
      case proto :: "GET" :: rest => buildRequest(proto,HttpMethods.GET,rest)
      case proto :: "POST" :: rest => buildRequest(proto,HttpMethods.POST,rest)
      case proto :: "PUT" :: rest => buildRequest(proto,HttpMethods.PUT,rest)
      case proto :: "DELETE" :: rest => buildRequest(proto,HttpMethods.DELETE,rest)

      case proto :: "AGET" :: rest => buildRequest(proto,HttpMethods.GET,rest,async=true)
      case proto :: "APOST" :: rest => buildRequest(proto,HttpMethods.POST,rest,async=true)
      case proto :: "APUT" :: rest => buildRequest(proto,HttpMethods.PUT,rest,async=true)
      case proto :: "ADELETE" :: rest => buildRequest(proto,HttpMethods.DELETE,rest,async=true)

      case proto :: rest => buildRequest(proto,HttpMethods.GET,rest)      
    }
  }

  val request = parseUri(uri)

  def ->(r:Request) = {
    val req = HttpRequest(method = r.verb, uri = r.uri, headers = r.getHeaders, 
      entity = if(r.body.isDefined) HttpEntity(ContentTypes.`application/json`,r.body.get) else HttpEntity.Empty
    )
    //entity = HttpEntity(ContentTypes.`application/json`)
    log.info(s"-> ${req}")

    val f = for {
      rsp <- Http().singleRequest(req)
      r <- if(rsp.status == StatusCodes.OK || rsp.status == StatusCodes.Created) {
          val f = rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
          f.map(Success(_))
        } else 
          Future(Failure(new Exception(s"${rsp.status}")))        
    } yield r

    if(!request.async)
      Await.result(f,FiniteDuration(timeout,TimeUnit.MILLISECONDS)).map(_.utf8String)
    else 
      Success(f.toString) 
  }
   
  def toJob(job:Job,sess:LivySession) = job.copy(
    // id = job.id,
    // name = job.name,
    xid = sess.id.toString,
    state = sess.state,
    // src = job.src,
    log = Some(sess.log)
  )

  def toJob(job:Job,st:LivyStatement) = {
    val j = job.copy(
      state = st.state, //job.status,
      //src = st.code,
      result = st.output.map(o => o.status),
      tsStart = Some(st.started),
      tsEnd = Some(st.completed)
    )

    //log.info(s"${j.result}")

    j.result match {
      case Some("error") =>
        j.copy(output = st.output.map(o => o.ename.getOrElse("") + "\n" + o.traceback.get.mkString("\n")))
      case Some(_) => 
        j.copy(output = st.output.map(o => o.data.get.values.mkString("\n")))
      case None => 
        j
    }
  }

  def all():Try[Seq[Job]] = {
    val res = ->(Request(uri + "/sessions", HttpMethods.GET))
    log.info(s"res = ${res}")
    res.map(_.parseJson.convertTo[LivySessions].sessions.map(r => {
      Job(
        id = Util.UUID_0,
        name = "",
        xid = r.id.toString,
        state = r.state,
        src = "",
        log = Some(r.log)
      )
    }))
  }

  def get(xid:String):Try[LivySession] = {
    val res = ->(Request(uri + s"/sessions/${xid}", HttpMethods.GET))
    log.info(s"res = ${res}")
    res.map(r => r.parseJson.convertTo[LivySession])
  }

  def get(job:Job):Try[Job] = {
    get(job.xid).map(ls => toJob(job,ls))
  }

  def ask(job:Job):Try[Job] = {
    val xid = job.xid
    for {
      session <- {
        val res = this.->(Request(uri + s"/sessions/${xid}", HttpMethods.GET))
        log.info(s"res = ${res}")
        res
      }
      results <- {
        val res = this.->(Request(uri + s"/sessions/${xid}/statements", HttpMethods.GET))
        log.info(s"res = ${res}")
        res
      }
      job1 <- {
        val j = toJob(job,session.parseJson.convertTo[LivySession])
        val res = results.parseJson.convertTo[LivySessionResults]
        if(res.total_statements == 0)
          Success(j)
        else
          Success(toJob(j,res.statements.last))
      }
    } yield job1
  }

  // state: "starting" -> "idle"
  // run() can be executed only against "idle" state
  def create(name:String,conf:Map[String,String]=Map()):Try[Job] = {
    val ts0 = System.currentTimeMillis
    // https://aws.amazon.com/de/blogs/big-data/install-python-libraries-on-a-running-cluster-with-emr-notebooks/
    val config = Map(
      "spark.pyspark.python" -> "python3",
      "spark.pyspark.virtualenv.enabled" -> "true",
      "spark.pyspark.virtualenv.type" -> "native",
      "spark.pyspark.virtualenv.bin.path" -> "/usr/bin/virtualenv"
    )
    val res = ->(Request(uri + s"/sessions", HttpMethods.POST, 
      body = Some(LivySessionCreate(kind = "pyspark", name, conf ++ config).toJson.compactPrint)
    ))
    log.info(s"res = ${res}")
    res.map(r => 
      toJob(
        Job(id=UUID.random,name = name, ts0 = ts0),
        r.parseJson.convertTo[LivySession]
      )
    )
  }

  def del(xid:String):Try[String] = {
    val res = ->(Request(uri + s"/sessions/${xid}" , HttpMethods.DELETE))
    log.info(s"res = ${res}")
    res.map(r => r.parseJson.convertTo[LivySessionRes].msg)
  }

  def del(job:Job):Try[Job] = {
    del(job.xid).map(r => job.copy(state = "deleted"))
  }

  // state: "waiting" -> 
  def run(job:Job,script:String,inputs:Map[String,String]=Map()):Try[Job] = 
    run(job.xid,script,inputs)
    .map(r => toJob(job,r))

  def run(xid:String,script:String,inputs:Map[String,String]):Try[LivyStatement] = {
    log.info(s"running: \n${"-".repeat(40)}\n${script}\n${"-".repeat(40)}\n")
    val res = ->(Request(uri + s"/sessions/${xid}/statements", HttpMethods.POST, 
      body = Some(LivySessionRun(code = script.replaceAll("\\\\n","\n")).toJson.compactPrint)
    ))
    log.info(s"res = ${res}")
    res.map(r => r.parseJson.convertTo[LivyStatement])
  }

  def submit(name:String,script:String,conf:Map[String,String]=Map(),inputs:Map[String,Any]=Map(),poll:Long):Try[Job] = {      
    // create source block with all expected variables
    
    // var src0 = JobEngine.dataToVars(inputs).map( _ match {
    //   case(code,"") =>
    //     code
    //   case(name,value) =>
    //     s"${name} = ${value}"
    // }).mkString("\n")

    // val src = src0 + "\n" +
    //   os.read(os.Path(script.stripPrefix("file://"),os.pwd))

    // val src = JobEngine.toSrc(script,inputs)
    // log.info(s"src=${src}")
    
    val j0 = create(name,conf).map(_.copy(src = script))
    j0
  }

  // def submit(name:String,script:String,conf:Seq[String]=Seq(),inputs:Seq[String]=Seq(),poll:Long):Try[Job] = {
      
  //   // create source block with all expected variables
  //   var src0 = JobEngine.dataToVars(inputs).map( _ match {
  //     case(code,"") =>
  //       code
  //     case(name,value) =>
  //       s"${name} = ${value}"
  //   }).mkString("\n")

  //   val src = src0 + "\n" +
  //     os.read(os.Path(script.stripPrefix("file://"),os.pwd))

  //   log.info(s"src=${src}")
    
  //   val j0 = create(name,JobEngine.dataToConf(conf))

  //   Future {
  //     for {
  //       j1 <- j0

  //       j2 <- {
  //         var j:Try[Job] = get(j1)
  //         while(j.isSuccess && j.get.state == "starting") {                  
  //           Thread.sleep(poll)
  //           j = get(j1)
  //         } 
  //         j
  //       }
        
  //       j3 <- {
  //         run(j2,src)
  //       }

  //       j4 <- {
  //         var j:Try[Job] = ask(j3)

  //         while(j.isSuccess && j.get.state != "available") {
  //           Thread.sleep(poll)
  //           j = ask(j3)
  //         } 
  //         j
  //       }
  //       j5 <- {
  //         j4.result match {
  //           case Some("error") => 
  //             log.error(s"Job: Error=${j4.output.getOrElse("")}")
  //           case Some("ok") =>
  //             log.info(s"Job: OK=${j4.output.getOrElse("")}")
  //           case _ => 
  //             log.info(s"Job: Unknown=${j4.result}: output=${j4.output.getOrElse("")}")
  //         }              

  //         del(j4)
  //       }
  //     } yield j4
  //   }

  //   j0
  // }
  
}
