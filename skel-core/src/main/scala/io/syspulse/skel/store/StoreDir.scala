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

  override def +(e:E):Try[E] = { 
    if( ! loading)
      writeFile(e)
    else
      Success(e)
  }

  def toKey(id:String):P

  override def del(id:P):Try[P] = { 
    if( ! loading) 
      delFileById(id.toString).map(_ => id)
    else
      Success(id)
  }

  def write(data:String,name:String,subdir:String=""):Try[StoreDir[E,P]] = try {
    val f = os.Path(dir + subdir,os.pwd) / name
    os.write.over(f,data)
    Success(this)
  } catch {
    case e:Exception =>
      log.error(s"failed to write: ${e}")
      Failure(e)
  }

  def writeFile(e:E):Try[E] = write(e.toJson.compactPrint,s"${getKey(e)}.json","").map(_ => e)
  // try {
  //   val f = os.Path(dir,os.pwd) / s"${getKey(e)}.json"    
  //   os.write.over(f,e.toJson.compactPrint)
  //   Success(e)
  // } catch {
  //   case e:Exception =>
  //     log.error(s"failed to write: ${e}")
  //     Failure(e)
  // }
  
  def delFileById(id:String):Try[String] = {
    try {
      os.remove(os.Path(dir,os.pwd) / s"${id}.json")
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
      case None => all.foreach(e => writeFile(e))
    }
    Success(this)
  }

  def clean():Try[StoreDir[E,P]] = clear()
  def clear():Try[StoreDir[E,P]] = {
    loading = true
    all.foreach(e => delFile(e))
    loading = false
    Success(this)
  }

  def loaded() = {}

  def load():Unit = load(this.dir,"")

  def load(dir:String,hint:String=""):Unit = {
    val storeDir = os.Path(dir,os.pwd)
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
      .flatten // files

    loading = true
    ee.foreach(e => this.+(e))
    loading = false

    log.info(s"Loaded store: ${size}")
    loaded()
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
    ee.foreach( e => StoreDir.this.+(e))    
  }

  def watch(dir:String) = {
    import better.files._
    import io.methvin.better.files._
    import io.methvin.watcher.hashing.FileHasher
    import java.nio.file.{Path, StandardWatchEventKinds => EventType, WatchEvent}
    import scala.concurrent.ExecutionContext.Implicits.global

    @volatile
    var modifying:Option[File] = None
    
    // try to prevent modifications by touching file
    val watcher = new RecursiveFileMonitor(
      File(dir),
      fileHasher = Some(FileHasher.LAST_MODIFIED_TIME)) {
      override def onCreate(file: File, count: Int) = {
        if(!file.isDirectory && (!modifying.isDefined || modifying.get != file)) {
          log.info(s"${file}: added")
          modifying = Some(file)

          // this will not load on ext4 !
          loading = true
          addAsFile(file.toString)          
          loading = false  
        }
      }
      override def onModify(file: File, count: Int) = {
        if(!file.isDirectory) {
          log.info(s"${file}: modified")
          
          modifying = Some(file)

          loading = true
          
          addAsFile(file.toString)          
          loading = false

          modifying = None
        }
      }

      override def onDelete(file: File, count: Int) = {        
        if(!file.isDirectory) {
          val id = file.nameWithoutExtension
          log.info(s"${file}: deleted")
          StoreDir.this.del(toKey(id))
        }
      }
    }

    watcher.start()
    log.info(s"watching: ${dir}")
  }

}