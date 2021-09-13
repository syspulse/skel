package io.syspulse.skel.scrap.emu

import java.time.Clock 
import io.jvm.uuid._
// import scalatags.Text.all._                                                                                                                                                                                 
// import scalatags.Text.tags2
import upickle._

import io.syspulse.skel.config._

case class Data(id:EmuStore.ID,name:String)

import scala.concurrent.ExecutionContext.global  

object EmuStore {
  type ID = String
  
  implicit val emuStoreRW: upickle.default.ReadWriter[Data] = upickle.default.macroRW
  
  var emuStoreDB:Map[ID,Data] = Map()
  
  def add(name:String):Data = {
    val data = Data(
      UUID.random.toString,
      name = name)
    

    // if(remoteUrl.isEmpty())
    //   emuStoreDB = emuStoreDB + (data.dataId -> data)
    // else {
    //   val r = requests.post(s"${remoteUrl}/api/v1/data",
    //     headers=Map("Content-Type"->"application/json"),
    //     data=upickle.default.write(data)
    //   )

    //   println(s"p=${p}")
    // }

    data
  }

  def add(data:Data):Data = {
    emuStoreDB = emuStoreDB + (data.id -> data)
    data 
  }
}

case class EmuRoutes() extends cask.Routes{
  import EmuStore._


  val CORS = Seq("Access-Control-Allow-Origin" -> "*")

  val NONE = UUID("00000000-0000-0000-0000-000000000000")

  // =================================================== EmuStore ===
  case class EmuStoreRequest(dataId:String)
  case class EmuStoreResponse(dataId:String,name:String)
  def EmuStoreNoneResponse(dataId:String="") = EmuStoreResponse(dataId,"")
  implicit val emuStoreRequestRW: upickle.default.ReadWriter[EmuStoreRequest] = upickle.default.macroRW
  implicit val emuStoreResponseRW: upickle.default.ReadWriter[EmuStoreResponse] = upickle.default.macroRW
  
  def retrying[T](max:Int,delay:Long, default:Option[T], code: => Option[T]):Option[T] =
    Range(1,max).foldLeft[Option[T]](None)((r,i) => { if(r.isDefined) r else {val r1=code; if(!r1.isDefined) Thread.sleep(delay); r1}}).orElse(default)

  @cask.get("/api/v1/data/:dataId")
  def emuStore(dataId:String,retry:Int=3,delay:Int=1000) = {
    println(s"\n${dataId}")

    val u = retrying(3,250L,None,
      { 
        //emuStoreDB.get(UUID.fromString(dataId))
        emuStoreDB.get(dataId) 
      }
    )

    val r = 
      if(u.isDefined)
        EmuStoreResponse(dataId,name = u.get.name)
      else 
        EmuStoreNoneResponse(dataId = dataId)
        
    println(s"Data: ${dataId}: ${r}")

    cask.Response(upickle.default.write(r),headers=CORS)
  }

  @cask.post("/api/v1/data")
  def postData(request: cask.Request) = {
    println(s"\n${request}")

    val d:Data = upickle.default.read[Data](request.text())
        
    println(s"data: ${d}")

    EmuStore.add(d)

    cask.Response(upickle.default.write(d),headers=CORS)
  }
  
  @cask.get("/info")
  def info() = {
    this.getClass.getName()+"\n"
  }

  @cask.get("/")
  def index() = {
    cask.Redirect("/web/index.html")
  }

  @cask.get("/login")
  def login() = {
    cask.Abort(401)
  }

  @cask.staticFiles("/web",headers = Seq("Cache-Control" -> "max-age=14400"))
  def staticFileRoutes() = "web"

  initialize()
}

abstract class EmuMain(routes: Seq[cask.main.Routes]) extends cask.Main{ 
  val allRoutes = routes ++ Seq(EmuRoutes())

  val confuration = Configuration.withPriority(Seq(new ConfigurationEnv,new ConfigurationProp, new ConfigurationAkka))

  override def port = confuration.getInt("PORT").getOrElse(30004)
  override def host = confuration.getString("HOST").getOrElse("0.0.0.0")

  override def main(args0: Array[String]) = {
    if(args0.size>0)
      println(s"${args0.mkString(",")}")

    // Docker arguments fix
    val args = 
      if(args0.size>0)
        if(args0(0).size==0)
          args0.tail
        else
          args0
      else
        args0

    super.main(args)
  }
}


// === Custom =========================================================================================================
case class NPP() extends cask.Routes{
  
  // Does not work if it is just extended !
  val rootUrl = "/MEDO-PS"
  @cask.get(rootUrl)
  def npp() = {
    cask.Redirect(s"${rootUrl}/index.php.html")
  }

  @cask.staticFiles(s"${rootUrl}/index.php.html",headers = Seq("Cache-Control" -> "max-age=14400"))
  def nppIndex() = s"web${rootUrl}/index.php.html"

  @cask.staticFiles(s"${rootUrl}/popup.php",headers = Seq("Cache-Control" -> "max-age=14400"))
  def nppPopup() = s"web${rootUrl}/popup.php"

  initialize()
}

object AppEmu extends EmuMain(Seq(NPP())) {  
  
}
