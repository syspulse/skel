package io.syspulse.skel.blockchain

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}

import java.io.FileWriter
import java.io.FileReader
import scala.util.Using

case class Label(
  addr:String,               
  tags:Set[String],
  sid:Option[String] = Some("ext"),
  ts:Long = System.currentTimeMillis,
  name:Option[String] = None,
)

trait LabelLoader {
  def load(file:String):Try[Vector[Label]]
  def load():Try[Vector[Label]]
}

class EtherscanLabelLoader(defaultFile:String = "store_labels/combinedAllLabels.json") extends LabelLoader {
  val log = Logger(getClass)

  def load(file:String):Try[Vector[Label]] = {
    log.info(s"Loading store: '${file}'")
    try {
      val aa = {
        val line  = os.read(os.Path(file,os.pwd))
        
          val j = ujson.read(line)

          j.obj.map { case(addr,data) => {            
            val tags = data("labels").arr.map(_.str).toSet
            val name = data("name").str
            Label(addr.toLowerCase,tags,Option("etherscan"),System.currentTimeMillis,Some(name))
          }}

      }.toVector

      log.info(s"Store: '${file}': ${aa.size}")
      Success(aa)
    } catch {
      case e:Exception => 
        log.error(s"failed to load: '${file}':",e)
        Failure(e)
    }
  }

  def load():Try[Vector[Label]] = {
    load(defaultFile)
  }
}

class ExtLabelLoader(defaultDir:String = "store_labels/") extends ListLoader("") {  
  override def load(dir:String):Try[Vector[Label]] = {
    val dir0 = os.Path(dir,os.pwd)
    
    try {

      log.info(s"Loading store: ${dir0}")

      val labels = if(os.isFile(dir0)) {
        // this is a file, try to load a dir from a file as content      
        val data = os.read(dir0)
        decode(data)
        
      } else {
                    
        val all = os.walk(dir0)
          .filter(_.toIO.isFile())
          .sortBy(_.toIO.lastModified())
          .map(f => {
            log.info(s"Loading file: ${f}")
            val fileName = f.toIO.getName()
            (os.read(f),fileName)
          })
          .foldLeft(Vector.empty[Label]){ case(all,(fileData,fileName)) => 
            decode(fileData) match {
              case Success(labels) => all ++ labels
              case Failure(e) => all
            }
          }
        Success(all)
      }
      
      log.info(s"Loaded store: '${dir}': ${labels.map(_.size).getOrElse(0)}")
      labels

    } catch {
      case e:Exception => 
        log.error(s"failed to load: '${dir0}'",e)
        Failure(e)
    }
  }

  override def load():Try[Vector[Label]] = {
    load(defaultDir)
  }
}

class ListLoader(list:String) extends LabelLoader {
  val log = Logger(getClass)

  def load(list:String):Try[Vector[Label]] = decode(list)

  def decode(list:String):Try[Vector[Label]] = {
    val ll = list.split("\n")
      .filter(!_.isBlank())
      .filter(!_.startsWith("#"))
      .flatMap(line => {
        line.split(",").toList match {
          case addr :: name :: tags :: Nil => 
            Some((addr.trim,name.trim,tags.split(";").filter(_.nonEmpty).map(_.trim).toSet,None,System.currentTimeMillis))
          case addr :: name :: Nil => 
            Some((addr.trim,name.trim,Set.empty[String],None,System.currentTimeMillis))
          case addr :: _ => 
            Some((addr.trim.stripPrefix("\"").stripSuffix("\""),"",Set.empty[String],None,System.currentTimeMillis))
          case s => 
            log.warn(s"failed to parse: ${s}")
            None
        }
      }
      .map { case(addr,name,tags,sid,ts) =>
        Label(addr.toLowerCase,tags,Some("ext"),System.currentTimeMillis,name = Some(name))
      }
    )

    Success(ll.toVector)
  }

  def load():Try[Vector[Label]] = {
    load(list)
  }
}

trait LabelStore {
  def ?(addr:String):Try[Label]
  def +(label:Label):Try[Label]
  def size:Int
  
  protected def getDefaultLoaders():Seq[LabelLoader] = Seq(new EtherscanLabelLoader())

  def sorted:Seq[Label]

  def getId():String
  def getTags():Set[String]
}

class LabelStoreMem(sid:String,tags:Set[String],addr:Seq[String], loaders0:Seq[LabelLoader] = Seq.empty ) extends LabelStore {

  override def toString = s"LabelMem(sid=${sid},tags=${tags},[${labels.size}],${if(labels.size < 10) labels.keys else "..."})"
  
  var labels:Map[String,Label] = {
    val loaders = (if(loaders0.size > 0) 
      loaders0
    else
      getDefaultLoaders()
    )
    
    val addr1 = loaders.flatMap { loader =>    
      loader.load() match {
        case Success(labels) => labels
        case Failure(e) => 
          Vector.empty
      }      
    }.map(label => 
      label.addr -> label
    ).toMap
    
    val addr2 = addr.map(addr => 
      addr.toLowerCase -> Label(addr,tags,None,System.currentTimeMillis)
    ).toMap

    addr1 ++ addr2
  }

  override def ?(addr:String):Try[Label] = {
    labels.get(addr.toLowerCase) match {
      case Some(label) => Success(label)
      case None => Failure(new Exception(s"Label not found: ${addr}"))
    }
  }

  override def +(label:Label):Try[Label] = {
    labels = labels + (label.addr.toLowerCase -> label)
    Success(label)
  }

  override def size:Int = labels.size

  def sorted:Seq[Label] = labels.values.toSeq.sortBy(- _.ts)

  def getId():String = this.sid
  def getTags():Set[String] = this.tags
}
