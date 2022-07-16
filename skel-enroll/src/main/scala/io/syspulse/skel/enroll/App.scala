package io.syspulse.skel.enroll

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.crypto.Eth
import io.syspulse.skel.util.Util

object App {
  val log = Logger(s"${this}")  
  //val system: ActorSystem[Command] = ActorSystem(EnrollFlow(), "EnrollSystem")

  def main(args:Array[String]):Unit = {    
    args.toList match {
      case "start" :: Nil => 
        val eid = EnrollSystem
          .withAutoTables()
          .start(
            "START,START_ACK,EMAIL,EMAIL_ACK,CONFIRM_EMAIL,CONFIRM_EMAIL_ACK,CREATE_USER,CREATE_USER_ACK,FINISH,FINISH_ACK",
            None)

        println(eid)
      
      case "email" :: eid :: email :: Nil => 
        EnrollSystem.addEmail(UUID(eid),email)
      
      case "confirm" :: eid :: code :: Nil => 
        EnrollSystem.confirmEmail(UUID(eid),code)

      case eid :: Nil => 
        val s = EnrollSystem.summary(UUID(eid))
        println(s"summary: ${eid}: ${s}")

      case "continue" :: eid :: Nil => 
        val s = EnrollSystem.continue(UUID(eid))
        println(s"summary: ${eid}: ${s}")

      case _ => 
        val eid = EnrollSystem
          .withAutoTables()
          
    }
  } 
  
}