package io.syspulse.skel.plugin.runtime

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.plugin.runtime._
import io.syspulse.skel.plugin._

class ClassRuntime() extends PluginRuntime {
  val log = Logger(s"${this}")
  
  def spawn(p:PluginDescriptor):Try[Plugin] = {
    log.info(s"spawn: ${p}")
    val rr = for {
      
      runtime <- try {
        val c = p.init

        log.info(s"spawning: class=${c}")
        val cz = Class.forName(c)

        // constructor is always Plugin
        val args = Array(p)
        val argsStr = args.map(_.getClass).toSeq
        
        cz.getConstructors().find { c => 
          val ctorStr = c.getParameters().map(_.getParameterizedType).toSeq
          
          // expecting one argument
          val b = ctorStr.size == 1
          log.info(s"class=${cz}: ctor=${c}: args=(${ctorStr.size}): '${argsStr.toString}'=='${ctorStr.toString}'")          
          b
        } match {
          case Some(ctor) => 
            val instance = ctor.newInstance(args:_*)
            
            log.debug(s"'${c}' => ${instance}")
            val e = instance.asInstanceOf[Plugin]
            Success(e)
            
          case None => 
            Failure(new Exception(s"constructor not resolved: ${c}: ${cz}"))
        }        
        
      } catch {
        case e:Exception => Failure(e)
      }
    } yield runtime

    rr    
  }

}
