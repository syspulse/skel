package io.syspulse.skel.npp

import io.syspulse.skel.util.Util
import io.syspulse.skel.flow._

import upickle._
import upickle.default._
import upickle.default.{ReadWriter => RW, macroRW}

class NppPrint(file:String = "/dev/stdout",format:String="csv") extends Stage[NppData]("NPP-Print") {
  
  def exec(flow:Flow[NppData]): Flow[NppData] = {
    flow.data.radiation.foreach{ r => 
      format match {
        case "json" => {          
          val json = write(r)
          println(s"${json}")
        }
        case "csv" => println(s"${Util.toCSV(r)}")
        case _ => println(s"${r}")
      }
    }

    flow
  }
}