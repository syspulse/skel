# Ammonite 2.12 !

import $ivy.`com.mchange:consuela_2.12:0.4.2`
import com.mchange.sc.v1.consuela.ethereum.jsonrpc._
import com.mchange.sc.v1.consuela.ethereum
import com.mchange.sc.v1.consuela
import com.mchange.sc.v1.consuela._
import com.mchange.sc.v1.consuela.ethereum
import com.mchange.sc.v1.consuela.ethereum.ethabi

import requests._

val API_KEY=sys.env.get("API_KEY").getOrElse("")
val contract = "0x1f9840a85d5af5bf1d1762f925bdaddc4201f984"

os.pwd

// export ABI:
// curl -i https://api.etherscan.io/api?module=contract&action=getabi&address=0x1f9840a85d5af5bf1d1762f925bdaddc4201f984&format=raw
val json = get(s"https://api.etherscan.io/api?module=contract&action=getabi&address=${contract}&format=raw&apikey=${API_KEY}").text
//val json = os.read(os.pwd / "ABI-UNI.json")

val uni_abi = Abi(json)

//"0x11aa".decodeHex

val tx_input = "0xa9059cbb000000000000000000000000f6bdeb12aba8bf4cfbfc2a60c805209002223e22000000000000000000000000000000000000000000000005a5a62f156c710000".decodeHex

val d = ethabi.decodeFunctionCall(uni_abi,tx_input.toImmutableSeq)

val (func,data) = d.get

println(data)
