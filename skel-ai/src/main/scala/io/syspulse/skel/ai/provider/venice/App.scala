package io.syspulse.skel.ai.provider.venice

import java.util.Base64
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.{Success, Failure,Try}

import io.syspulse.skel
import io.syspulse.skel.config._

import io.syspulse.skel.ai.core.VeniceURI

case class Config(
  host:String="0.0.0.0",
  port:Int=8080,
  uri:String = "/api/v1/venice",

  datastore:String = "mem://",

  ai:String = "venice://chroma",
  model:String = "",

  num:Int = 1,
  max:Int = 1,

  background:String = VeniceAi.DEFAULT_BACKGROUND,
  moderation:String = VeniceAi.DEFAULT_MODERATION,
  
  compression:Int = VeniceAi.DEFAULT_OUTPUT_COMPRESSION,
  format:String = VeniceAi.DEFAULT_OUTPUT_FORMAT,  
  formatResponse:String = VeniceAi.DEFAULT_RESPONSE_FORMAT,
  size:String = VeniceAi.DEFAULT_SIZE,
  timeout:Long = 120000,
  retry:Option[Int] = None,
  user:Option[String] = None,
  out:String = "images/",
  prefix:String = "img",
  
  width:Option[Int] = None,//Some(VeniceAi.DEFAULT_WIDTH_X),
  height:Option[Int] = None, //Some(VeniceAi.DEFAULT_HEIGHT_X),
  aspect:Option[String] = Some("9:16"),
  resolution:Option[String] = None,
  cfgScale:Option[Double] = None,
  negativePrompt:Option[String] = None,  
  safe:Option[Boolean] = Some(false),
  seed:Option[Int] = None,
  steps:Option[Int] = None,
  style:Option[String] = None,
  enableWebSearch:Option[Boolean] = Some(false),
  exif:Option[Boolean] = Some(true),
  watermark:Option[Boolean] = Some(false),
  loraStrength:Option[Int] = None,
  returnBinary:Boolean = false,
  quality:Option[String] = None,

  delay:Long = 0L,

  cmd:String = "imagex",
  params: Seq[String] = Seq(),
)

object App extends skel.Server {

  def main(args:Array[String]):Unit = {

    val d = Config()
    val c = Configuration.withPriority(Seq(
      new ConfigurationAkka,
      new ConfigurationProp,
      new ConfigurationEnv,
      new ConfigurationArgs(args,"ai-venice","",
        ArgString('h', "http.host",s"listen host (def: ${d.host})"),
        ArgInt('p', "http.port",s"listern port (def: ${d.port})"),
        ArgString('u', "http.uri",s"api uri (def: ${d.uri})"),

        ArgString('d', "datastore",s"Datastore [mem://,gl://,ofac://] (def: ${d.datastore})"),

        ArgString('a', "ai",s"AI provider (def: ${d.ai})"),
        ArgString('m', "model",s"Image model (def: from ai uri or ${VeniceAi.DEFAULT_MODEL_IMG})"),
        
        ArgString('_', "background",s"Background (def: ${d.background})"),
        ArgString('_', "moderation",s"Moderation (def: ${d.moderation})"),
        
        ArgInt('_', "compression",s"Output compression (def: ${d.compression})"),
        ArgString('_', "format",s"Output format (def: ${d.format})"),
        ArgString('_', "quality",s"Quality (def: ${d.quality})"),
        ArgString('_', "format.response",s"Response format (def: ${d.formatResponse})"),
        ArgString('_', "size",s"Size (def: ${d.size})"),
        ArgLong('_', "timeout",s"Timeout in msec (def: ${d.timeout})"),
        ArgInt('_', "retry",s"Retry count (def: from ai uri)"),
        ArgString('_', "user",s"User id (def: none)"),        
        
        ArgInt('_', "width",s"Image width in pixels (def: ${VeniceAi.DEFAULT_WIDTH_X})"),
        ArgInt('_', "height",s"Image height in pixels (def: ${VeniceAi.DEFAULT_HEIGHT_X})"),
        ArgString('_', "aspect",s"Aspect ratio, e.g. 16:9 (def: ${d.aspect})"),
        ArgString('_', "resolution",s"Resolution tier 1K|2K|4K (def: ${d.resolution})"),
        ArgDouble('_', "cfg.scale",s"CFG scale (def: ${d.cfgScale})"),
        ArgString('_', "negative.prompt",s"Negative prompt (def: ${d.negativePrompt})"),
        ArgString('_', "safe",s"Safe mode true|false (def: ${d.safe})"),
        ArgInt('_', "seed",s"Random seed (def: ${d.seed})"),
        ArgInt('_', "steps",s"Inference steps (def: ${d.steps})"),
        ArgString('_', "style",s"Style preset (def: ${d.style})"),
        ArgString('_', "enable.web.search",s"Enable web search true|false (def: ${d.enableWebSearch})"),
        ArgString('_', "exif",s"Embed EXIF metadata (def: ${d.exif})"),
        ArgString('_', "watermark",s"Hide watermark true|false (def: ${d.watermark})"),
        ArgInt('_', "lora.strength",s"Lora strength 0-100 (def: ${d.loraStrength})"),
        ArgString('_', "return.binary",s"Return binary response true|false (def: ${d.returnBinary})"),
        ArgString('_', "quality",s"Native quality low|medium|high (def: ${d.quality})"),

        ArgInt('n', "num",s"Number of images to generate (def: ${d.num})"),
        ArgInt('_', "max",s"Number of images per request/model (def: ${d.max})"),
        ArgString('o', "out",s"Output directory (def: ${d.out})"),
        ArgString('_', "prefix",s"Output file prefix (def: ${d.prefix})"),

        ArgLong('_', "delay",s"Delay between requests in msec (def: ${d.delay})"),

        ArgCmd("image","Generate image (OpenAI-compatible API)"),
        ArgCmd("imagex","Generate image (native Venice API)"),

        ArgParam("<params>",""),
        ArgLogging(),
        ArgConfig(),
      ).withExit(1)
    )).withLogging()

    implicit val config = Config(
      host = c.getString("http.host").getOrElse(d.host),
      port = c.getInt("http.port").getOrElse(d.port),
      uri = c.getString("http.uri").getOrElse(d.uri),

      datastore = c.getString("datastore").getOrElse(d.datastore),

      ai = c.getString("ai").getOrElse(d.ai),
      model = c.getString("model").getOrElse(d.model),
      
      max = c.getInt("max").getOrElse(d.max),
      num = c.getInt("num").getOrElse(d.num),
      
      background = c.getString("background").getOrElse(d.background),
      moderation = c.getString("moderation").getOrElse(d.moderation),
      
      compression = c.getInt("compression").getOrElse(d.compression),
      format = c.getString("format").getOrElse(d.format),
      quality = c.getString("quality"),
      formatResponse = c.getString("format.response").getOrElse(d.formatResponse),
      size = c.getString("size").getOrElse(d.size),
      timeout = c.getLong("timeout").getOrElse(d.timeout),
      retry = c.getInt("retry"),
      user = c.getString("user"),
      out = c.getString("out").getOrElse(d.out),
      prefix = c.getString("prefix").getOrElse(d.prefix),

      width = c.getInt("width").orElse(d.width),
      height = c.getInt("height").orElse(d.height),
      aspect = c.getString("aspect"),
      resolution = c.getString("resolution"),
      cfgScale = c.getDouble("cfg.scale"),
      negativePrompt = c.getString("negative.prompt"),      
      safe = c.getString("safe").map(_.toBoolean).orElse(d.safe),
      seed = c.getInt("seed"),
      steps = c.getInt("steps"),
      style = c.getString("style"),
      enableWebSearch = c.getString("enable.web.search").map(_.toBoolean).orElse(d.enableWebSearch),
      exif = c.getString("exif").map(_.toBoolean).orElse(d.exif),
      watermark = c.getString("watermark").map(_.toBoolean).orElse(d.watermark),
      loraStrength = c.getInt("lora.strength"),
      returnBinary = c.getString("return.binary").map(_.toBoolean).getOrElse(d.returnBinary),      

      delay = c.getLong("delay").getOrElse(d.delay),

      cmd = c.getCmd().getOrElse(d.cmd),
      params = c.getParams(),
    )

    log.info(s"Config: ${config}")

    val prompt0 = config.params.mkString(" ")
    val prompt = if(prompt0.startsWith("file://")) {
      val file = os.Path(prompt0.stripPrefix("file://"), os.pwd)
      os.read(file)
    } else 
      prompt0

    if(prompt.isBlank) {
      Console.err.println(s"Empty prompt")
      sys.exit(1)
    }

    log.info(s"prompt = '${prompt}'")

    val aiUri = VeniceURI(config.ai)
    val provider = new VeniceAi(aiUri)
    val model = if(config.model.nonEmpty) config.model else aiUri.getModel().getOrElse(VeniceAi.DEFAULT_MODEL_IMG)
    val modelX = if(config.model.nonEmpty) config.model else aiUri.getModel().getOrElse(VeniceAi.DEFAULT_MODEL_IMG_X)
    
    val r = config.cmd match {
      case "image" =>
        image(model, prompt)(provider, config)
      case "imagex" =>
        imageX(modelX, prompt, config.prefix)(provider, config)
      case cmd =>
        Console.err.println(s"Unknown command: '${cmd}'")
        sys.exit(1)
    }

    Console.err.println(s"${r}")
    sys.exit(0)
  }

  def image(model:String, prompt:String, prefix:String = "img")(provider:VeniceAi, config:Config) = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    
    val timeout = config.timeout    

    (0 to config.num.min(VeniceAi.MAX_IMAGES_DAY) - 1)
      .grouped(config.max.min(VeniceAi.MAX_IMAGES))
      .foreach( range => {
        val ii = range.mkString(",")

        Console.err.print(s"Generating [${ii}]/${config.num}: ${model}")
        
        Try {
          val r = provider.generateImage(
              prompt = prompt,
              background = config.background,
              model = model,
              moderation = config.moderation,
              n = range.size,
              outputCompression = config.compression,
              outputFormat = config.format,
              quality = config.quality,
              responseFormat = config.formatResponse,
              size = config.size,
              timeout = timeout,
              retry = config.retry,
              user = config.user,
              style = config.style
            )
          
          val res = Await.result(r,FiniteDuration(timeout, TimeUnit.MILLISECONDS))

          res.data.zipWithIndex.foreach { case (img, i) =>
            val bytes = Base64.getDecoder.decode(img.b64_json)
            val idx = range.head + i

            Console.err.print(s"created=${res.created}, size=${bytes.size}: ")

            val hash = skel.util.Util.sha256(bytes)
            val name = s"${prefix}-${idx}-${hash}.${config.format}"
            
            // create output directory if it does not exist
            os.makeDir.all(os.Path(config.out, os.pwd))

            val file = os.Path(config.out, os.pwd) / name
            os.write(file, bytes)

            Console.err.println(s" : ${Console.GREEN}${file}${Console.RESET}")
          }

          if(config.delay > 0) {
            Thread.sleep(config.delay)
          }
          
        } match {
          case Success(r) =>
            // Console.err.println(s"${r}")
          case Failure(e) =>
            Console.err.println(s"Error: ${Console.RED}${e}${Console.RESET}")
        }

      })
  }

  def imageX(model:String, prompt:String, prefix:String = "img")(provider:VeniceAi, config:Config) = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val timeout = config.timeout
    val format = config.format
    val quality = config.quality
    val width = config.width
    val height = config.height

    var ok = 0
    var err = 0
    
    (0 to config.num.min(VeniceAi.MAX_IMAGES_DAY) - 1)
      .grouped(config.max.min(VeniceAi.MAX_VARIANTS))
      .foreach { range =>

        val ii = range.mkString(",")
        val max = range.size
        Console.err.print(s"Generating [${ii}]/${config.num}: model=${model},format=${format},quality=${quality},dim=${width}x${height},Lora=(${config.loraStrength},${config.cfgScale},${config.steps}),exif=${config.exif}")

        Try {
          val r = provider.generateImageX(
            prompt = prompt,
            model = model,
            format = Some(format),
            width = config.width,
            height = config.height,
            cfgScale = config.cfgScale,
            embedExifMetadata = config.exif,
            hideWatermark = config.watermark,
            loraStrength = config.loraStrength,
            negativePrompt = config.negativePrompt,
            returnBinary = config.returnBinary,
            variants = Some(max),
            safeMode = config.safe,
            seed = config.seed,
            steps = config.steps,
            style = config.style,
            aspectRatio = config.aspect,
            resolution = config.resolution,
            quality = quality,
            enableWebSearch = config.enableWebSearch,
            timeout = timeout,
            retry = config.retry
          )

          val res = Await.result(r, FiniteDuration(timeout, TimeUnit.MILLISECONDS))

          res.images.zipWithIndex.foreach { case (img, i) =>
            val bytes = Base64.getDecoder.decode(img)
            val idx = range.head + i

            Console.err.print(s"id=${res.id}, size=${bytes.size}, timing=${res.timing.total}ms: ")

            val hash = skel.util.Util.sha256(bytes)
            val name = s"${prefix}-${idx}-${hash}.${format}"

            os.makeDir.all(os.Path(config.out, os.pwd))

            val file = os.Path(config.out, os.pwd) / name
            os.write(file, bytes)

            Console.err.println(s" : ${Console.GREEN}${file}${Console.RESET}")
          }

          if(config.delay > 0) {
            Thread.sleep(config.delay)
          }

        } match {
          case Success(_) =>
            ok += 1
          case Failure(e) =>
            Console.err.println(s"Error: ${Console.RED}${e}${Console.RESET}")
            err += 1
        }
      }

      (ok, err)
  }

}
