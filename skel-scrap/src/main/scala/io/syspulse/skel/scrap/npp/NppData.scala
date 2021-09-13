package io.syspulse.skel.scrap.npp

import scala.collection.mutable

case class NppData(var indexFile:String="",popupFiles:mutable.Map[String,String]=mutable.Map(),var radiation:List[Radiation]=List())
