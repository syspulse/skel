package io.syspulse.skel.ai.provider.venice

import io.syspulse.skel.ai.core.VeniceURI
import io.syspulse.skel.ai.provider.openai.OpenAiLike
import io.syspulse.skel.ai.core.AiURI
import io.syspulse.skel.service.JsonCommon
import spray.json._
import akka.http.scaladsl.model.StatusCodes
import scala.concurrent.ExecutionContext
import scala.concurrent.Future


// {
//   "created": 1713833628,
//   "data": [
//     {
//       "b64_json": "iVBORw0KGgoAAAANSUhEUgAA..."
//     }
//   ]
// }

case class Venice_GenerateImageData(
  b64_json: String
)
case class Venice_GenerateImageRes(
  created: Long,
  data: Seq[Venice_GenerateImageData]
)

// {
//   "prompt": "A beautiful sunset over mountain ranges",
//   "background": "auto",
//   "model": "default",
//   "moderation": "auto",
//   "n": 1,
//   "output_compression": 100,
//   "output_format": "png",
//   "quality": "auto",
//   "response_format": "b64_json",
//   "size": "auto",
//   "style": "natural",
//   "user": "user123"
// }
case class Venice_GenerateImageReq(
  prompt: String,
  background: String,
  model: String,
  moderation: String,
  n: Int,
  output_compression: Int,
  output_format: String,
  quality: String,
  response_format: String,
  size: String,
  style: String,
  user: String
)

case class Venice_GenerateImageXReq(
  model: String,
  prompt: String,
  cfg_scale: Option[Double] = None,
  embed_exif_metadata: Option[Boolean] = None,
  format: Option[String] = None,
  height: Option[Int] = None,
  hide_watermark: Option[Boolean] = None,
  lora_strength: Option[Int] = None,
  negative_prompt: Option[String] = None,
  return_binary: Option[Boolean] = None,
  variants: Option[Int] = None,
  safe_mode: Option[Boolean] = None,
  seed: Option[Int] = None,
  steps: Option[Int] = None,
  style_preset: Option[String] = None,
  aspect_ratio: Option[String] = None,
  resolution: Option[String] = None,
  quality: Option[String] = None,
  enable_web_search: Option[Boolean] = None,
  width: Option[Int] = None
)

case class Venice_GenerateImageXTiming(
  inferenceDuration: Double,
  inferencePreprocessingTime: Double,
  inferenceQueueTime: Double,
  total: Double
)

case class Venice_GenerateImageXRes(
  id: String,
  images: Seq[String],
  timing: Venice_GenerateImageXTiming
)

object Venice_Json extends JsonCommon {
  implicit val jf_venice_generate_image_data = jsonFormat1(Venice_GenerateImageData)
  implicit val jf_venice_generate_image_res = jsonFormat2(Venice_GenerateImageRes)
  implicit val jf_venice_generate_image_req = jsonFormat12(Venice_GenerateImageReq)
  implicit val jf_venice_generate_image_x_timing = jsonFormat4(Venice_GenerateImageXTiming)
  implicit val jf_venice_generate_image_x_res = jsonFormat3(Venice_GenerateImageXRes)
  implicit val jf_venice_generate_image_x_req = jsonFormat20(Venice_GenerateImageXReq)
}

object VeniceAi {
  val MAX_IMAGES_DAY = 1000
  val MAX_IMAGES = 1
  val MAX_VARIANTS = 4

  val DEFAULT_MODEL_IMG = "default"
  val DEFAULT_MODEL_IMG_X = "venice-sd35"
  val DEFAULT_OUTPUT_FORMAT = "webp"  
  val DEFAULT_RESPONSE_FORMAT = "b64_json"
  val DEFAULT_SIZE = "auto"
  val DEFAULT_STYLE = "natural"
  val DEFAULT_QUALITY = "auto"
  val DEFAULT_BACKGROUND = "auto"
  val DEFAULT_MODERATION = "auto"  
  val DEFAULT_OUTPUT_COMPRESSION = 100
  val DEFAULT_WIDTH_X = 1080
  val DEFAULT_HEIGHT_X = 1920
  val DEFAULT_RATIO = "9:16"
  val MAX_DIMENSION_X = 1280

  val DEFAULT_MODEL_ASPECT_MAP = Map(
    "9:16" -> (720, 1280),
    "16:9" -> (1280, 720),
  )
  val ASPECT_MODEL_MAP = Map(
    "venice-sd35" -> DEFAULT_MODEL_ASPECT_MAP,

    "chroma" -> Map(
      "square" -> (1080, 1080),      
      "landscape" -> (1264, 848),
      "3:2" -> (1264, 848),
      "cinema" -> (1280, 720),
      "16:9" -> (1280, 720),
      "widescreen" -> (1344, 576),
      "21:9" -> (1344, 576),

      "tall" -> (720, 1280),
      "9:16" -> (720, 1280),
      "portrait" -> (848,1264),
      "4:3" -> (848,1264),

      "instagram" -> (960, 1280),
      "3:4" -> (960, 1280),      
    )
  )

  def aspectToWidthHeight(model: String, aspect: String, maxDim: Int = MAX_DIMENSION_X): (Int, Int) = {
    ASPECT_MODEL_MAP.get(model).flatMap(m => m.get(aspect)).getOrElse(DEFAULT_MODEL_ASPECT_MAP.get(aspect).getOrElse((maxDim, maxDim)))
  }
  
  val STYLES = Seq(
    "3D Model",
    "Analog Film",
    "Anime",
    "Cinematic",
    "Comic Book",
    "Craft Clay",
    "Digital Art",
    "Enhance",
    "Fantasy Art",
    "Isometric Style",
    "Line Art",
    "Lowpoly",
    "Neon Punk",
    "Origami",
    "Photographic",
    "Pixel Art",
    "Texture",
    "Advertising",
    "Food Photography",
    "Real Estate",
    "Abstract",
    "Cubist",
    "Graffiti",
    "Hyperrealism",
    "Impressionist",
    "Pointillism",
    "Pop Art",
    "Psychedelic",
    "Renaissance",
    "Steampunk",
    "Surrealist",
    "Typography",
    "Watercolor",
    "Fighting Game",
    "GTA",
    "Super Mario",
    "Minecraft",
    "Pokemon",
    "Retro Arcade",
    "Retro Game",
    "RPG Fantasy Game",
    "Strategy Game",
    "Street Fighter",
    "Legend of Zelda",
    "Architectural",
    "Disco",
    "Dreamscape",
    "Dystopian",
    "Fairy Tale",
    "Gothic",
    "Grunge",
    "Horror",
    "Minimalist",
    "Monochrome",
    "Nautical",
    "Space",
    "Stained Glass",
    "Techwear Fashion",
    "Tribal",
    "Zentangle",
    "Collage",
    "Flat Papercut",
    "Kirigami",
    "Paper Mache",
    "Paper Quilling",
    "Papercut Collage",
    "Papercut Shadow Box",
    "Stacked Papercut",
    "Thick Layered Papercut",
    "Alien",
    "Film Noir",
    "HDR",
    "Long Exposure",
    "Neon Noir",
    "Silhouette",
    "Tilt-Shift"
  )
}

class VeniceAi(uri:VeniceURI) extends OpenAiLike {
  import Venice_Json._

  override def getUri():AiURI = uri

  def generateImage(
    prompt: String,
    background: String = VeniceAi.DEFAULT_BACKGROUND,
    model: String = VeniceAi.DEFAULT_MODEL_IMG,
    moderation: String = VeniceAi.DEFAULT_MODERATION,
    n: Int = VeniceAi.MAX_IMAGES,
    outputCompression: Int = VeniceAi.DEFAULT_OUTPUT_COMPRESSION,
    outputFormat: String = VeniceAi.DEFAULT_OUTPUT_FORMAT,
    quality: Option[String] = None,
    responseFormat: String = VeniceAi.DEFAULT_RESPONSE_FORMAT,
    size: String = VeniceAi.DEFAULT_SIZE,
    timeout:Long = getTimeout(),
    retry:Option[Int] = None,
    user: Option[String] = None,
    style: Option[String] = None
  )(implicit ec: ExecutionContext):Future[Venice_GenerateImageRes] = {

    val url = s"${getUri().apiUrl}/v1/images/generations"

    val body = Venice_GenerateImageReq(
      prompt = prompt,
      background = background,
      model = model,
      moderation = moderation,
      n = n,
      output_compression = outputCompression,
      output_format = outputFormat,
      quality = quality.getOrElse(VeniceAi.DEFAULT_QUALITY),
      response_format = responseFormat,
      size = size,
      style = style.getOrElse(VeniceAi.DEFAULT_STYLE),
      user = user.getOrElse("")
    ).toJson.compactPrint

    log.debug(s"body=${body}")
    log.info(s"model=${model},prompt=[${prompt.size}]: '${prompt.take(32).replaceAll("\n","\\\\n")}...' -> ${url}")

    def attemptRequest(): Future[Venice_GenerateImageRes] = {
      httpRequest(
        url = url,
        body = body,
        headers = Seq(
          "Authorization" -> s"Bearer ${getUri().apiKey}"
        ),
        timeout = timeout
      ).flatMap { resp =>
        if (resp.status == StatusCodes.OK) {
          readResponseBody(resp).map { responseBody =>
            log.debug(s"${body}: ${responseBody}")
            responseBody.parseJson.convertTo[Venice_GenerateImageRes]
          }
        } else {
          readResponseBody(resp).flatMap { errorBody =>
            Future.failed(new Exception(s"HTTP ${resp.status}: ${errorBody}"))
          }
        }
      }
    }

    import io.syspulse.skel.util.Retry
    Retry.withRetryFuture(attemptRequest(), s"generateImage: '${prompt.take(32)}...'")(retry.getOrElse(getRetry()), 3000)(log, ec)
  }

  def resolveWidthHeight(
    model: String,
    width: Option[Int],
    height: Option[Int],
    aspectRatio: Option[String]
  ): (Option[Int], Option[Int]) = {
    val (dw, dh) = VeniceAi.aspectToWidthHeight(model,aspectRatio.getOrElse(VeniceAi.DEFAULT_RATIO))
    (width.orElse(Some(dw)), height.orElse(Some(dh)))
  }

  def generateImageX(
    prompt: String,
    model: String = VeniceAi.DEFAULT_MODEL_IMG_X,
    format: Option[String] = None,
    width: Option[Int] = None,
    height: Option[Int] = None,
    cfgScale: Option[Double] = None,
    embedExifMetadata: Option[Boolean] = None,
    hideWatermark: Option[Boolean] = None,
    loraStrength: Option[Int] = None,
    negativePrompt: Option[String] = None,
    returnBinary: Boolean = false,
    variants: Option[Int] = None,
    safeMode: Option[Boolean] = None,
    seed: Option[Int] = None,
    steps: Option[Int] = None,
    style: Option[String] = None,
    aspectRatio: Option[String] = None,
    resolution: Option[String] = None,
    quality: Option[String] = None,
    enableWebSearch: Option[Boolean] = None,
    timeout: Long = getTimeout(),
    retry: Option[Int] = None
  )(implicit ec: ExecutionContext): Future[Venice_GenerateImageXRes] = {

    val url = s"${getUri().apiUrl}/v1/image/generate"
    
    val useRatioTier = width.isEmpty && height.isEmpty && resolution.exists(_.nonEmpty)
    val (reqWidth, reqHeight, reqAspectRatio, reqResolution) =
      if (useRatioTier) {
        (None, None, aspectRatio, resolution)
      } else {
        val (rw, rh) = resolveWidthHeight(model, width, height, aspectRatio)
        (rw, rh, None, None)
      }
    // val reqWidth = width
    // val reqHeight = height
    // val reqAspectRatio = aspectRatio
    // val reqResolution = resolution

    val stylePreset = style.map(s => s.replaceAll("_"," "))

    val body = Venice_GenerateImageXReq(
      model = model,
      prompt = prompt,
      cfg_scale = cfgScale,
      embed_exif_metadata = embedExifMetadata,
      format = format,
      height = reqHeight,
      hide_watermark = hideWatermark,
      lora_strength = loraStrength,
      negative_prompt = negativePrompt,
      return_binary = Some(returnBinary),
      variants = variants,
      safe_mode = safeMode,
      seed = seed,
      steps = steps,
      style_preset = stylePreset,
      aspect_ratio = reqAspectRatio,
      resolution = reqResolution,
      quality = quality,
      enable_web_search = enableWebSearch,
      width = reqWidth
    ).toJson.compactPrint

    log.debug(s"body=${body}")
    log.info(s"model=${model},size=${reqWidth.getOrElse(-1)}x${reqHeight.getOrElse(-1)},aspect=${reqAspectRatio.getOrElse("")},prompt=[${prompt.size}]: '${prompt.take(32).replaceAll("\n","\\\\n")}...' -> ${url}")

    def attemptRequest(): Future[Venice_GenerateImageXRes] = {
      httpRequest(
        url = url,
        body = body,
        headers = Seq(
          "Authorization" -> s"Bearer ${getUri().apiKey}"
        ),
        timeout = timeout
      ).flatMap { resp =>
        if (resp.status == StatusCodes.OK) {
          readResponseBody(resp).map { responseBody =>
            log.debug(s"${body}: ${responseBody}")
            responseBody.parseJson.convertTo[Venice_GenerateImageXRes]
          }
        } else {
          readResponseBody(resp).flatMap { errorBody =>
            Future.failed(new Exception(s"HTTP ${resp.status}: ${errorBody}"))
          }
        }
      }
    }

    import io.syspulse.skel.util.Retry
    Retry.withRetryFuture(attemptRequest(), s"generateImageX: '${prompt.take(32)}...'")(retry.getOrElse(getRetry()), 3000)(log, ec)
  }

}