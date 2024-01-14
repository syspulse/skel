package io.syspulse.skel.plugin

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import akka.actor.typed.SupervisorStrategy

import io.syspulse.skel.plugin.runtime._
import io.syspulse.skel.plugin.store._

class PluginEngine(store:PluginStore) {
  val log = Logger(s"${this}")

  def spawn():Seq[Try[Runtime[_]]] = {
    store.all.map( p => 
      spawn(p)
    )
  }

  def spawn(p:Plugin):Try[Runtime[_]] = {
    p.typ match {
      case "class" | "jar" => 
        new ClassRuntime().spawn(p)

      case _ => Failure(new Exception(s"unknown type: ${p.typ}"))
    }
    // log.info(s"spawn: ${p}")
    // val rr = for {
      
    //   runtime <- try {
        
    //     log.debug(s"spawning: class=${p.typ}")
    //     val cz = Class.forName(p.typ)

    //     // constructor is always Plugin
    //     val args = Array(p)
    //     val argsStr = args.map(_.getClass).toSeq
        
    //     cz.getConstructors().find { c => 
    //       val ctorStr = c.getParameters().map(_.getParameterizedType).toSeq
          
    //       // expecting one argument
    //       val b = ctorStr.size == 1
    //       log.info(s"class=${cz}: ctor=${c}: args=(${ctorStr.size}): '${argsStr.toString}'=='${ctorStr.toString}'")          
    //       b
    //     } match {
    //       case Some(ctor) => 
    //         val instance = ctor.newInstance(args:_*)
            
    //         log.debug(s"'${p.typ}' => ${instance}")
    //         val e = instance.asInstanceOf[Runtime[_]]
    //         Success(e)
            
    //       case None => 
    //         Failure(new Exception(s"constructor not resolved: ${p.typ}: ${cz}"))
    //     }        
        
    //   } catch {
    //     case e:Exception => Failure(e)
    //   }
    // } yield runtime

    // rr
  }

  
  def start(r:Runtime[_]):Try[Runtime[_]] = {
    log.info(s"start: ${r}")
    
    // os.makeDir.all(os.Path(wfRuntimeDir,os.pwd))
    // createDataDir(plugin.getId)
    
    r.start()

    Success(r)
  }

  def stop(r:Runtime[_]):Try[Runtime[_]] = {
    log.info(s"stop: ${r}")

    r.stop()

    Success(r)
  }

}
