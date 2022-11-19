// Specify Eth node in $ETH_NODE envvar (e.g. export ETH_NODE=http://127.0.0.1:8545)
import scala.jdk.CollectionConverters._
import collection.mutable._
import os._

import $ivy.`com.google.guava:guava:29.0-jre`
import com.google.common.math.Quantiles._

val ethNode = Option(System.getenv("ETH_NODE")).getOrElse(s"""https://mainnet.infura.io/${Option(System.getenv("INFURA_KEY")).getOrElse("UNKNOWN-INFURA-KEY")}""")
Console.err.println(s"eth node: ${ethNode}")

val price = ujson.read(
  requests.get("https://api.coingecko.com/api/v3/simple/price?ids=ethereum&vs_currencies=usd",
               headers=Map("Content-Type"->"application/json")).text
  )
  .obj("ethereum").obj("usd").num

def h2d(s:String) = s.foldLeft(0.0)( (r,c) => 16*r + c.getNumericValue)
val json = ujson.read(
  requests.post(s"${ethNode}/graphql",
                data = """{ "query": "{ pending { transactions {gasPrice }}}"}""",
                headers=Map("Content-Type"->"application/json")).text
)
val costs = json
  .obj("data")
  .obj("pending")
  .obj("transactions")
  .arr.map(o=>o.obj("gasPrice").str.substring(2))
  .map(h2d(_).asInstanceOf[java.lang.Double])

val max = costs.max
val min = costs.min
val n = 100
val r = (max-min)/n
val hist = costs
  .map(c=>((c-min)/(max-min)*n))
  .groupBy(c=>c.toLong)
  .map(c=>(c._1,c._2.size)).toSeq
  .sortBy(_._1)
  .map(_._2)


val med = median().compute(costs.asJava) / 1E9
val perc = percentiles().index(95).compute(costs.asJava) / 1E9
val hh = hist.zipWithIndex.map{case(h,i) => {val gwei = ((i*r+min)/1E9).round; (gwei,h)}}

Console.err.println(s"""
min = ${min / 1E9} gwei,
max = ${max / 1E9} gwei,
med = $med gwei,
percentiles = $perc gwei,
hist = ${hh.foldLeft("\n")((txt,h) =>  txt + "  "+ "%d gwei ($%.2f) %d".format(h._1,h._1/1E9*price*21000,h._2) + "\n")}
""")

val hhCsv = s"gwei,pending\n" + hh.foldLeft("")((txt,h) => txt + s"${h._1},${h._2}\n")
os.write.over(pwd / "GWEI-histo.csv", hhCsv)
// print for | piping
println(hhCsv)


