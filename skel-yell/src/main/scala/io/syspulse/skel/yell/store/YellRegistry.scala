package io.syspulse.skel.yell.store

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.Command

import io.syspulse.skel.yell._
import io.syspulse.skel.yell.Yell.ID
import scala.util.Try

object YellRegistry {
  val log = Logger(s"${this}")
  
  final case class GetYells(replyTo: ActorRef[Yells]) extends Command
  final case class GetYell(id:ID,replyTo: ActorRef[Try[Yell]]) extends Command
  final case class SearchYell(txt:String,replyTo: ActorRef[List[Yell]]) extends Command
  
  final case class CreateYell(yellCreate: YellCreateReq, replyTo: ActorRef[Yell]) extends Command
  final case class RandomYell(replyTo: ActorRef[Yell]) extends Command

  final case class DeleteYell(id: ID, replyTo: ActorRef[YellActionRes]) extends Command
  
  // this var reference is unfortunately needed for Metrics access
  var store: YellStore = null //new YellStoreDB //new YellStoreCache

  def apply(store: YellStore = new YellStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: YellStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetYells(replyTo) =>
        replyTo ! Yells(store.all)
        Behaviors.same

      case GetYell(id, replyTo) =>
        replyTo ! store.?(id)
        Behaviors.same

      case SearchYell(txt, replyTo) =>
        replyTo ! store.??(txt)
        Behaviors.same


      case CreateYell(yellCreate, replyTo) =>
        val yell = Yell(System.currentTimeMillis(),yellCreate.level,yellCreate.area,yellCreate.text)
        val uid = Yell.uid(yell)
        
        val store1 = store.+(yell)

        replyTo ! yell
        registry(store1.getOrElse(store))

      case RandomYell(replyTo) =>
        
        //replyTo ! YellRandomRes(secret,qrImage)
        Behaviors.same

      
      case DeleteYell(id, replyTo) =>
        val store1 = store.del(id)
        replyTo ! YellActionRes(s"Success",Some(id))
        registry(store1.getOrElse(store))
    }
  }
}
