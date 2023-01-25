package io.syspulse.skel.crypto.eth.abi

import com.typesafe.scalalogging.Logger

import scala.util.Try
import scala.util.Success

import codegen.Decoder
import codegen.AbiDefinition
import os._
import scala.util.Failure

// abstract class AbiRepos[A <: AbiRepos[A]] {
//   //def decode(selector:String)
//   def withRepo(repo:AbiRepo):A
// }

// class AbiReposLoaded extends AbiRepos[AbiReposLoaded] {
// }

class AbiStoreRepo extends AbiStore {

  var repos:List[AbiStore] = List()
  
  // var functions:Map[String,String] = Map(
  //   "0xa9059cbb" -> "transfer",
  //   "0x23b872dd" -> "transferFrom",
  // )

  def size = repos.size

  def find(addr:String,function:String,name:String=""):Try[ContractAbi] = {
    repos.map(r => r.find(addr,function,name)).head
  }
  
  def withRepo(repo:AbiStore):AbiStoreRepo = {
    repos = repos :+ repo
    this
  }

  def load():Try[AbiStore] = {
    repos.foldLeft(Seq[Try[AbiStore]]())( (m,r)  => m :+ r.load() )
    Success(this)
  }

  def decodeInput(contract:String,input:String,selector:String) = {
    repos.foldLeft(Seq[Try[Seq[(String,String,Any)]]]())( (m,r)  => m :+ r.decodeInput(contract,input,selector) ).head
  }
}

object AbiStoreRepo {
  
  def build() = new AbiStoreRepo()
}

