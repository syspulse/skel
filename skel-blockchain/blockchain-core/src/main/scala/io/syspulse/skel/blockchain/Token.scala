package io.syspulse.skel.blockchain

import scala.util.{Try,Success,Failure}
import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util
import io.syspulse.skel.util.CacheIndexExpire

// compatible with old Token class
case class Token(
  addr:String,
  sym:String,
  dec:Int = 18,
  bid:String,
  icon:Option[String] = None,
)

// container
case class Coin(
  sym:String,
  icon:Option[String] = None,
  tokens:Map[String,Token] = Map(), // map of network -> addr  

  id:Option[String] = None,  // unique id (coingecko id)
  sid:Option[String] = None, // source id (coingecko)
  xid:Option[String] = None, // external id (e.g. co)  
) {
  // primary address
  def getAddr():String = tokens.get("ethereum").map(_.addr).getOrElse("")
  def dec:Int = tokens.get("ethereum").map(_.dec).getOrElse(18)
}

trait TokenProvider {
  
  def load():Try[Set[Coin]]
  def resolve(tokenId:String):Try[Coin]
}

class TokenProviderCoinGecko(apiKey0:Option[String]) extends TokenProvider {
  val log = Logger[this.type]
  val apiKey = apiKey0.orElse(sys.env.get("CG_API_KEY")).getOrElse("")

  def load():Try[Set[Coin]] = {
    Success(loadFromResources())
  }
  
  def decodeCoin(json:String):Try[Coin] = {
    try {
    //json.parseJson.convertTo[Token]
      val j = ujson.read(json) //.asInstanceOf[Token]

      val id = j.obj("id").str
      val symbol = j.obj("symbol").str.toUpperCase()
      val icon = j.obj.get("icon").flatMap(_.strOpt)

      val tokens = j.obj("detail_platforms").obj
        .flatMap{ case(chain,platform) => {
          try {

            val decimals = platform.obj("decimal_place").num.toInt
            val addr = platform.obj("contract_address").str.toLowerCase()

            Some(
              chain -> Token(
                addr,
                "",
                decimals,
                chain
              )
            )

          } catch {
            case e:Exception =>
              None
          }
        }}.toMap
      
      Success(
        Coin(
          symbol,
          icon,
          tokens = tokens,
          id = Some(id),
          sid = Some("cg"),
          xid = Some(id)
      ))

    } catch {
      case e:Exception =>
        Failure(e)
    }
  }

  def loadFromResources(file:String = "tokens.json"):Set[Coin] = {
    try {
      val txt = scala.io.Source.fromResource(file).getLines()
      
      val tt = txt.flatMap(json => {
        decodeCoin(json) match {
          case Success(c) => Some(c)
          case Failure(e) => None
        }
      }).toSet
      
      tt
    } catch {
      case e:Exception =>
        //log.error(s"could not load: ${file}",e)
        Set.empty[Coin]
    }
  }

  def resolve(tokenId:String):Try[Coin] = {    
    try {
      val url = s"https://api.coingecko.com/api/v3/coins/${tokenId}"
      log.info(s"${tokenId} -> '${url}'")
      val rsp = requests.get(
        url,
        headers = Seq(
          "x-cg-pro-api-key" -> s"${apiKey}",
          "Accept" -> "application/json",
        ),
        maxRedirects = 2
      )

      log.debug(s"rsp: ${rsp}")
            
      decodeCoin(rsp.text())

    } catch {
      case e: Exception => 
        log.error(s"failed to get token",e)
        Failure(e)
    }    
  }
  
}

class TokenProvidereDefault(tokens0:Set[Coin]) extends TokenProvider {  

  val tokens:Set[Coin] = tokens0 ++ Set(Token.USDT)

  def load():Try[Set[Coin]] = Success(tokens)
  
  def resolve(tokenId:String):Try[Coin] = tokens.find(_.sym == tokenId) match {
    case Some(c) => Success(c)
    case None => Failure(new Exception(s"not found: ${tokenId}"))
  }
}

class Tokens(providers:Seq[TokenProvider]) {
  val log = Logger[this.type]

  val tokensCache = new CacheIndexExpire[String,Coin,String](1000L * 60L * 60L * 24L * 30L) {
    def key(v:Coin):String = v.getAddr()
    def index(v:Coin):String = v.sym.toUpperCase  
  }
    
  def +(c:Coin) = {    
    // tokensCache = tokensCache + (token.addr.toLowerCase -> (tokensCache.getOrElse(token.addr.toLowerCase,Set()) + token))
    // tokensSym = tokensSym + (token.sym.toUpperCase -> (tokensSym.getOrElse(token.sym.toUpperCase,Set()) + token))    
    tokensCache.put(c)
  }

  def size = tokensCache.size
  def all():Iterable[Coin] = tokensCache.cache.values.map(_.v)

  def load() = {
    providers
      .flatMap(p => p.load().toOption)
      .filter(_.size > 0)
      .foreach(cc => {
        cc.foreach( c => {
          tokensCache.put(c)
        })
      })

    log.info(s"loaded tokens: ${tokensCache.size}")
  }

  def resolve(tokenId:String,sym:Option[String],dec:Option[Int],chain0:Option[Blockchain]):Set[Token] = {
    var chain = chain0

    val c0 = if(tokenId.startsWith("0x")) {
      //first try to resolve from address
      if(!chain.isDefined) chain = Some(Blockchain.ETHEREUM)

      val t = tokensCache
        .get(tokenId.toLowerCase)
        .orElse(
          providers
            .iterator
            .map(p => p.resolve(tokenId))
            .find(_.isSuccess)
            .flatMap(_.toOption)
        )

      if(t.isDefined)
        t
      else
        //Some(Set(Token(tokenId.toLowerCase,sym.getOrElse(""),dec.getOrElse(18),chain.map(_.name).getOrElse(""))))
        // don't resolve token if not found
        None
    } else {
      // try to resolve by symbol
      //tokensSym.get(tokenId.toUpperCase)
      tokensCache
        .findByIndex(tokenId.toUpperCase)              
    }
     
    if(!c0.isDefined ) {
      // try to resolve from external source
      Set()
    } else {      
      Token.coinToToken(c0.get,chain)
    }
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
      case _ => Set()
    }    
  }

  def find(addr:String) = tokensCache.get(addr.toLowerCase).map(Token.coinToToken(_))

  def findCoin(addr:String) = tokensCache.get(addr.toLowerCase)
}

object Token {
  val USDT = Coin(
    "USDT",
    Some("UNKNOWN"),
    Map("ethereum" -> Token("0xdac17f958d2ee523a2206206994597c13d831ec7","USDT",6,Blockchain.ETHEREUM.name))
  )
    
  val default = new Tokens(Seq(
    // new TokenProvidereDefault(Set(USDT)),
    new TokenProviderCoinGecko(None)
  ))
  default.load()

  def size = default.size
  def resolve(tokenId:String):Set[Token] = default.resolve(tokenId)
  def find(addr:String) = default.find(addr)
  def findCoin(addr:String) = default.findCoin(addr)

  def coinToToken(c:Coin,chain:Option[Blockchain] = None):Set[Token] = {
    if(chain.isDefined) {
      c.tokens.get(chain.get.name).map(t => Token(
        t.addr,
        c.sym,
        t.dec,
        t.bid,
        c.icon
      )).toSet
    } else {
      c.tokens.values.map(t => Token(
        t.addr,
        c.sym,
        t.dec,
        t.bid,
        c.icon
      )).toSet
    }
  }

}