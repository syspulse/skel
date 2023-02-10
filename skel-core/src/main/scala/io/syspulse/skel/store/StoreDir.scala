package io.syspulse.skel.store

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import io.jvm.uuid._

import com.typesafe.scalalogging.Logger

import spray.json._
import DefaultJsonProtocol._

abstract class StoreDir[E,P](dir:String = "store/")(implicit fmt:JsonFormat[E],fmt2:Option[ExtFormat[E]]=None) extends Store[E,P] {
  val log = Logger(s"${this}")

  @volatile var loading = false

  override def +(e:E):Try[StoreDir[E,P]] = { 
    if( ! loading)
      writeFile(e).map(_ => this)
    else
      Success(this)
  }

  override def del(id:P):Try[Store[E,P]] = { 
    delFileById(id).map(_ => this)
  }

  def writeFile(e:E):Try[E] = try {
    os.write.over(os.Path(dir,os.pwd) / s"${getKey(e)}.json",e.toJson.compactPrint)
    Success(e)
  } catch {
    case e:Exception => Failure(e)
  }
  
  def delFileById(id:P):Try[P] = try {
    os.remove(os.Path(dir,os.pwd) / s"${id}.json")
    Success(id)
  } catch {
    case e:Exception => Failure(e)
  }

  def delFile(e:E):Try[E] = delFileById(getKey(e)).map(_ => e)
  
  def flush(e:Option[E]):Try[StoreDir[E,P]] = {
    e match {
      case Some(e) => writeFile(e)
      case None => all.foreach(e => writeFile(e))
    }
    Success(this)
  }

  def clean():Try[StoreDir[E,P]] = {
    all.foreach(e => delFile(e))
    Success(this)
  }

  def load(dir:String) = {
    val storeDir = os.Path(dir,os.pwd)
    if(! os.exists(storeDir)) {
      os.makeDir.all(storeDir)
    }
    
    log.info(s"Loading dir store: ${storeDir}")

    val vv = os.walk(storeDir)
      .filter(_.toIO.isFile())
      .map(f => {
        log.info(s"Loading file: ${f}")
        os.read(f)
      })
      .map(fileData => fileData.split("\n").map { data =>
        try {
          val c = data.parseJson.convertTo[E]
          log.debug(s"c=${c}")
          Seq(c)
        } catch {
          case e:Exception => 
            if(fmt2.isDefined) {
              fmt2.get.decode(data) match {
                case Success(e) => e
                case Failure(en) => 
                  log.error(s"could not parse data (${fmt2}): ${data}",e)
                  Seq()
              }
            } else {
              log.error(s"could not parse data (${fmt}): ${data}",e); 
              Seq()
            }
        }
      })
      .flatten // file
      .flatten // files

    loading = true
    vv.foreach(v => this.+(v))
    loading = false

    log.info(s"Loaded store: ${size}")
  }

}