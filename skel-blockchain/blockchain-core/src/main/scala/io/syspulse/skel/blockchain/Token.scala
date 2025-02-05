package io.syspulse.blockchain

import scala.util.{Try,Success,Failure}
import scala.jdk.CollectionConverters._
import io.syspulse.skel.Ingestable
import io.syspulse.skel.util.Util

case class Token(addr:String,sym:String,dec:Int = 18,bid:String,icon:Option[String] = None)

object Token {
    
  val USDT = new Token("0xdac17f958d2ee523a2206206994597c13d831ec7","USDT",6,Blockchain.ETHEREUM.name)
    
  var tokensAddr: Map[String,Set[Token]]= Map(
    USDT.addr -> Set(USDT),
  )

  var tokensSym: Map[String,Set[Token]]= Map(
    USDT.sym -> Set(USDT),
  )
  
  def +(token:Token) = {    
    tokensAddr = tokensAddr + (token.addr.toLowerCase -> (tokensAddr.getOrElse(token.addr.toLowerCase,Set()) + token))
    tokensSym = tokensSym + (token.sym.toUpperCase -> (tokensSym.getOrElse(token.sym.toUpperCase,Set()) + token))    
  }

  def size = tokensSym.size

  load()

  // load from resources
  def load() = {
    loadFromResources().foreach(t => this.+(t))
    //println(s"Tokens: ${TOKENS}")
  }

  def loadFromResources(file:String = "tokens.json") = {
    try {
      val txt = scala.io.Source.fromResource(file).getLines()
      
      val tt = txt.flatMap(json => {
        try {
          val j = ujson.read(json) //.asInstanceOf[Token]

          val id = j.obj("id").str
          val symbol = j.obj("symbol").str.toUpperCase()
          val icon = j.obj("icon").strOpt

          val tt = j.obj("detail_platforms").obj
            .flatMap{ case(chain,platform) => {
              try {

                val decimals = platform.obj("decimal_place").num.toInt
                val addr = platform.obj("contract_address").str.toLowerCase()

                Some(Token(addr,symbol,decimals,chain,icon))

              } catch {
                case e:Exception =>
                  None
              }
            }}.toSeq
  
          tt
        } catch {
          case e:Exception =>
            Seq()
        }
      })

      
      tt.toSeq
    } catch {
      case e:Exception =>
        //log.error(s"could not load: ${file}",e)
        Seq()
    }
  }

  
  def resolve(tokenId:String,sym:Option[String],dec:Option[Int],chain:Option[Blockchain]):Set[Token] = {
    
    val t0 = if(tokenId.startsWith("0x")) {
      //first try to resolve from address
      val t = tokensAddr.get(tokenId.toLowerCase)
      if(t.isDefined)
        t
      else
        //Some(Set(Token(tokenId.toLowerCase,sym.getOrElse(""),dec.getOrElse(18),chain.map(_.name).getOrElse(""))))
        // don't resolve token if not found
        None
    } else {
      // try to resolve by symbol
      tokensSym.get(tokenId.toUpperCase)
    }
     
    if(!t0.isDefined ) {
      // try to resolve from external source
      Set()
    } else
      t0.get
  }
  
  
  // token can be in any of the formats:  
  // USDT  
  // 0x1234567890abcdef1234567890abcdef1234567890abcdef
  // 0x1234567890abcdef1234567890abcdef1234567890abcdef:USDT:18
  // 0x1234567890abcdef1234567890abcdef1234567890abcdef:USDT
  // ethereum:0x1234567890abcdef1234567890abcdef1234567890abcdef
  // ethereum:0x1234567890abcdef1234567890abcdef1234567890abcdef:USDT
  // ethereum:USDT
  def resolve(tokenId:String):Set[Token] = {
    tokenId.trim.split("\\:").toList match {

      case addr :: sym :: dec :: Nil if(addr.startsWith("0x")) => resolve(addr,Some(sym),Some(dec.toInt),None)
      case addr :: sym :: Nil if(addr.startsWith("0x")) => resolve(addr,Some(sym),None,None)

      case chain :: chainId :: addr :: sym :: dec :: Nil => resolve(addr,Some(sym),Some(dec.toInt),Some(Blockchain(chain,Some(chainId))))
      case chain :: chainId :: addr :: sym :: Nil => resolve(addr,Some(sym),None,Some(Blockchain(chain,Some(chainId))))
      case chain :: chainId :: tid :: _ => resolve(tid,None,None,Some(Blockchain(chain,Some(chainId))))
      case chain :: tid :: _ => resolve(tid,None,None,Some(Blockchain(chain,None)))
      
      case tid :: Nil if(tid.startsWith("0x")) => resolve(tid,None,None,None)
      case tid :: Nil => resolve(tid,None,None,None)
    
    }    
  }

  def find(addr:String) = tokensAddr.get(addr.toLowerCase).map(_.head)
}