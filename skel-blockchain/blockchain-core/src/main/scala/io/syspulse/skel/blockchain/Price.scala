package io.syspulse.blockchain

import scala.util.{Try,Success,Failure}
import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util
import io.syspulse.skel.util.CacheExpire

case class Price(addr:String,price:Double,sym:Option[String] = None)

trait PriceProvider {
  
  def load():Try[Set[Price]]
  def resolve(addr:String):Try[Price]
}

class PriceProviderPriceGecko(apiKey0:Option[String]) extends PriceProvider {
  val log = Logger[this.type]
  val apiKey = apiKey0.orElse(sys.env.get("CG_API_KEY")).getOrElse("")

  def load():Try[Set[Price]] = {
    Success(loadFromResources())
  }
  
  def decodePrice(json:String):Try[Price] = {
    try {
    //json.parseJson.convertTo[Price]
      val j = ujson.read(json) //.asInstanceOf[Price]
      
      val (addr,price) = j.obj("data").obj("attributes").obj("token_prices").obj.head

      Success(
        Price(
          addr,
          price.str.toDouble          
        )
      )
    } catch {
      case e:Exception =>
        Failure(e)
    }
  }

  def loadFromResources(file:String = "prices.json"):Set[Price] = {
    try {
      val txt = scala.io.Source.fromResource(file).getLines()
      
      val tt = txt.flatMap(json => {
        decodePrice(json) match {
          case Success(c) => Some(c)
          case Failure(e) => None
        }
      }).toSet
      
      tt
    } catch {
      case e:Exception =>
        //log.error(s"could not load: ${file}",e)
        Set.empty[Price]
    }
  }

  def resolve(addr:String):Try[Price] = {
    val network:String = "eth"
    
    try {
      val url = s"https://pro-api.coingecko.com/api/v3/onchain/simple/networks/${network}/token_price/${addr}"
      
      log.info(s"${addr} -> '${url}'")
      
      val rsp = requests.get(
        url,
        headers = Seq(
          "x-cg-pro-api-key" -> s"${apiKey}",
          "Accept" -> "application/json",
        ),
        maxRedirects = 2
      )

      log.debug(s"rsp: ${rsp}")
            
      decodePrice(rsp.text())

    } catch {
      case e: Exception => 
        log.error(s"failed to get token",e)
        Failure(e)
    }    
  }  
}

class PriceProvidereDefault(tokens0:Set[Price]) extends PriceProvider {  

  val tokens:Set[Price] = tokens0 ++ Set(Price.USDT)

  def load():Try[Set[Price]] = Success(tokens)
  
  def resolve(pid:String):Try[Price] = tokens.find(_.sym == Some(pid)) match {
    case Some(c) => Success(c)
    case None => Failure(new Exception(s"not found: ${pid}"))
  }
}

class Prices(providers:Seq[PriceProvider]) {
  val log = Logger[this.type]

  val priceCache = new CacheExpire[String,Price,String](1000L * 60L * 60L * 1L) {
    def key(v:Price):String = v.addr
    def index(v:Price):String = v.sym.getOrElse("")
  }
    
  def +(c:Price) = {    
    priceCache.put(c)
  }

  def size = priceCache.size

  def load() = {
    providers
      .flatMap(p => p.load().toOption)
      .filter(_.size > 0)
      .foreach(cc => {
        cc.foreach( c => {
          priceCache.put(c)
        })
      })

    log.info(s"loaded prices: ${priceCache.size}")
  }

  def resolve(pid:String,sym:Option[String],dec:Option[Int],chain0:Option[Blockchain]):Option[Price] = {
    var chain = chain0

    val c0 = if(pid.startsWith("0x")) {
      //first try to resolve from address
      if(!chain.isDefined) chain = Some(Blockchain.ETHEREUM)

      priceCache
        .get(pid.toLowerCase)
        .orElse(
          providers
            .iterator
            .map(p => p.resolve(pid))
            .find(_.isSuccess)
            .flatMap(_.toOption)
        )
        .map(p => {
          priceCache.put(p)
          p
        })

    } else {
      // try to resolve by symbol
      priceCache
        .findByIndex(pid.toUpperCase)
    }

    c0
  }
  
  def resolve(pid:String):Option[Price] = {
    pid.trim.split("\\:").toList match {

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

  def find(addr:String) = priceCache.get(addr.toLowerCase)
}

object Price {
  val USDT = Price("0xdac17f958d2ee523a2206206994597c13d831ec7",1.0,Some("USDT"))
    
  val default = new Prices(Seq(
    new PriceProvidereDefault(Set(USDT)),
    new PriceProviderPriceGecko(None)
  ))
  default.load()

  def size = default.size
  def resolve(pid:String):Option[Price] = default.resolve(pid)
  def find(addr:String) = default.find(addr)
}
