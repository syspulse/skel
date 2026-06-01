package io.syspulse.skel.store

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import io.jvm.uuid._
import java.util.concurrent.atomic.AtomicBoolean

import com.typesafe.scalalogging.Logger

import better.files._
import io.methvin.better.files._
import io.methvin.watcher.hashing.FileHasher
import java.nio.file.{Path, StandardWatchEventKinds => EventType, WatchEvent}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Await}
import scala.concurrent.duration.Duration

import spray.json._
import DefaultJsonProtocol._

abstract class StoreDir[E,P](dir:String = "store/")(implicit fmt:JsonFormat[E],fmt2:Option[ExtFormat[E]]=None) extends Store[E,P] {
  val log = Logger(s"${this}")

  @volatile var loading = false
  val writing = new AtomicBoolean(false)

  def getDir():String = dir

  override def +(e:E):Future[E] = {
    if( ! loading)
      Future.fromTry(writeFile(e))
    else
      Future.successful(e)
  }

  def toKey(id:String):P

  override def del(id:P):Future[P] = {
    if( ! loading)
      Future.fromTry(delFileById(id.toString).map(_ => id))
    else
      Future.successful(id)
  }

  def write(data:String,name:String,subdir:String=""):Try[StoreDir[E,P]] = try {
    val f = os.Path(getDir() + subdir,os.pwd) / name
    os.write.over(f,data)
    Success(this)
  } catch {
    case e:Exception =>
      log.error(s"failed to write: '${name}'",e)
      Failure(e)
  }

  def writeFile(e:E):Try[E] =
    write(e.toJson.compactPrint,s"${getKey(e)}.json","")
      .map(_ => e)

  def delFileById(id:String):Try[String] = {
    try {
      os.remove(os.Path(getDir(),os.pwd) / s"${id}.json")
      Success(id)
    } catch {
      case e:Exception =>
        log.error(s"failed to delete: ${e}")
        Failure(e)
    }
  }

  def delFile(e:E):Try[E] = delFileById(getKey(e).toString).map(_ => e)

  def flush(e:Option[E]):Try[StoreDir[E,P]] = {
    e match {
      case Some(e) => writeFile(e)
      case None => Store.fromFuture(all).getOrElse(Seq.empty).foreach(e => writeFile(e))
    }
    Success(this)
  }

  def clean():Try[StoreDir[E,P]] = clear()
  def clear():Try[StoreDir[E,P]] = {
    loading = true
    Store.fromFuture(all).getOrElse(Seq.empty).foreach(e => delFile(e))
    loading = false
    Success(this)
  }

  def loaded() = {}

  def load():String = load(getDir(),"")

  def load(dir:String,hint:String=""):String = {
    val dir0 = os.Path(dir,os.pwd)

    val storeDir = if(os.isFile(dir0)) {
      // this is a file, try to load a dir from a file as content
      // e.g. dir://LATEST.txt
      // LATEST.txt: /mnt/s3/2024/10/25/
      os.Path(os.read(dir0).trim(),os.pwd)
    } else
      dir0

    if(! os.exists(storeDir)) {
      os.makeDir.all(storeDir)
    }

    log.info(s"Loading dir store: ${storeDir}")

    val ee = os.walk(storeDir)
      .filter(_.toIO.isFile())
      .sortBy(_.toIO.lastModified())
      .map(f => {
        log.info(s"Loading file: ${f}")
        val fileName = f.toIO.getName()
        (os.read(f),fileName)
      })
      .map{ case(fileData,fileName) =>
        loadData(fileData,hint,fileName)
      }
      .flatten

    loading = true
    val futs = ee.map(e => this.+(e)).toSeq
    if(futs.nonEmpty)
      Await.result(Future.sequence(futs), Duration(60, "seconds"))
    loading = false

    log.info(s"Loaded store: ${Store.fromFuture(size).getOrElse(0L)}")
    loaded()
    storeDir.toString
  }

  def loadData(fileData:String,hint:String,fileName:String):Seq[E] = {
    val ee = fileData.split("\n").filter(!_.trim.isEmpty).map { data =>
      if(hint.isEmpty || data.contains(hint)) {
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
                  log.error(s"could not parse data with code=(${fmt2}): ${data}",en)
                  Seq()
              }
            } else {
              log.error(s"could not parse data (${fmt}): ${data}",e)
              Seq()
            }
        }
      } else
        // ignore
        Seq()
    }
    ee.toSeq.flatten
  }

  def addAsFile(f:String) = {
    val file = os.Path(f,os.pwd)
    log.info(s"Loading file: ${file}")
    val data = os.read(file)
    val ee = loadData(data,"",file.toIO.getName())
    val futs = ee.map(e => this.+(e)).toSeq
    if(futs.nonEmpty)
      Await.result(Future.sequence(futs), Duration(30, "seconds"))
  }

  def watch(dir:String):RecursiveFileMonitor = {

    @volatile
    var modifying:Option[File] = None

    // try to prevent modifications by touching file
    val watcher = new RecursiveFileMonitor(
      File(dir),
      fileHasher = Some(FileHasher.LAST_MODIFIED_TIME)) {
      override def onCreate(file: File, count: Int) = {
        if(!writing.get() && !file.isDirectory && (!modifying.isDefined || modifying.get != file)) {
          log.info(s"${file}: added (writing=${writing.get()},modifying=${modifying})")
          modifying = Some(file)

          // this will not load on ext4 !
          loading = true
          addAsFile(file.toString)
          loading = false
        }
      }
      override def onModify(file: File, count: Int) = {
        if(!writing.get() && !file.isDirectory) {
          log.info(s"${file}: modified (writing=${writing.get()})")

          modifying = Some(file)

          loading = true

          addAsFile(file.toString)
          loading = false

          modifying = None
        }
      }

      override def onDelete(file: File, count: Int) = {
        if(!writing.get() && !file.isDirectory) {
          val id = file.nameWithoutExtension
          log.info(s"${file}: deleted (wrtiing=${writing.get()})")
          StoreDir.this.del(toKey(id))
        }
      }
    }

    watcher.start()
    log.info(s"watching: ${dir}")
    watcher
  }

}
