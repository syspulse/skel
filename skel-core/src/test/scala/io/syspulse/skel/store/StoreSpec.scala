package io.syspulse.skel.store

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util

import spray.json._
import spray.json.DefaultJsonProtocol
import io.syspulse.skel.service.JsonCommon
import scala.util.Try
import scala.util.Success
import scala.util.Failure

case class Entity(id:String,ts:Long = 0L,name:String = "name1")

object EntityJson extends JsonCommon  {
  import DefaultJsonProtocol._

  implicit val jf_1 = jsonFormat3(Entity.apply)
}

import EntityJson._

class EntityStoreDir(dir:String,preload:Boolean = false) extends StoreDir[Entity,String](dir) {
  var ee: Map[String,Entity] = Map()

  if(preload) load(dir)
  
  def getKey(e:Entity) = e.id
  override def +(e:Entity):Try[EntityStoreDir] = {
    ee = ee + (e.id -> e)
    super.+(e).map(_ => this)
  }
  override def -(e:Entity):Try[EntityStoreDir] = del(e.id)
  override def del(id:String):Try[EntityStoreDir] = {
    ee = ee - id
    super.del(id).map(_ => this)
  }
  override def ?(id:String):Try[Entity] = ee.get(id) match {
    case Some(e) => Success(e)
    case None => Failure(new Exception(s"not found: ${id}"))
  }
  override def all:Seq[Entity] = ee.values.toSeq
  override def size:Long = ee.size
}

class StoreSpec extends AnyWordSpec with Matchers {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath
  val dir1 = s"${testDir}/store/store-1/"
  os.makeDir.all(os.Path(dir1,os.pwd))

  // clear directory
  os.remove(os.Path(dir1,os.pwd) / s"*.json")

  "StoreDir" should {  
    "store and remove Entity to file" in {
      val store = new EntityStoreDir(dir1,preload = false)

      os.exists(os.Path(dir1,os.pwd) / s"1.json") === (false)

      val e = Entity("ID-1",10L,"name-1")

      val r1 = store.+(e)
      r1 === (Success[EntityStoreDir](_))
      os.exists(os.Path(dir1,os.pwd) / s"1.json") === (true)

      val r2 = store.-(e)
      r2 === (Success[EntityStoreDir](_))
      os.exists(os.Path(dir1,os.pwd) / s"1.json") === (false)
    } 

    "store and read Entity from file" in {
      val store1 = new EntityStoreDir(dir1,preload = false)
      os.exists(os.Path(dir1,os.pwd) / s"2.json") === (false)
      val e1 = Entity("ID-2",20L,"name-2")
      val r1 = store1.+(e1)
      r1 === (Success[EntityStoreDir](_))
      os.exists(os.Path(dir1,os.pwd) / s"2.json") === (true)

      val store2 = new EntityStoreDir(dir1,preload = false)
      val r2 = store2.?("ID-2")
      r2 === (Success[Entity](Entity("ID-2",20L,"name-2")))
      os.exists(os.Path(dir1,os.pwd) / s"2.json") === (true)
      
      val r3 = store2.?("ID-3")
      info(s"${r3}")
      r3 === (Failure[Entity](_))
    } 

    "load entities on startup" in {
      os.remove(os.Path(dir1,os.pwd) / s"*.json")

      val store1 = new EntityStoreDir(dir1,preload = false)
      for( i <- 0 to 9)
        store1.+(Entity(s"ID-${i}",i,s"name-${i}"))
            
      val store2 = new EntityStoreDir(dir1,preload = true)
      val r2 = store2.size
      r2 === (10)
        
    } 
  }
}
