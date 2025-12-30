package io.syspulse.skel.telegram

import spray.json._
import io.syspulse.skel.service.JsonCommon

object TelegramJson extends JsonCommon {
  // API Response models
  implicit val jf_telegram_user: RootJsonFormat[TelegramUser] = jsonFormat5(TelegramUser)
  implicit val jf_telegram_chat: RootJsonFormat[TelegramChat] = jsonFormat6(TelegramChat)
  implicit val jf_telegram_photo_size: RootJsonFormat[TelegramPhotoSize] = jsonFormat5(TelegramPhotoSize)
  implicit val jf_telegram_video: RootJsonFormat[TelegramVideo] = jsonFormat9(TelegramVideo)
  implicit val jf_telegram_document: RootJsonFormat[TelegramDocument] = jsonFormat6(TelegramDocument)
  implicit val jf_telegram_audio: RootJsonFormat[TelegramAudio] = jsonFormat8(TelegramAudio)
  implicit val jf_telegram_voice: RootJsonFormat[TelegramVoice] = jsonFormat5(TelegramVoice)

  // Recursive format for reply_to_message
  implicit lazy val jf_telegram_api_message: JsonFormat[TelegramApiMessage] = lazyFormat(jsonFormat13(TelegramApiMessage))

  implicit val jf_telegram_update: RootJsonFormat[TelegramUpdate] = jsonFormat4(TelegramUpdate)
  implicit val jf_telegram_get_updates_response: RootJsonFormat[TelegramGetUpdatesResponse] = jsonFormat2(TelegramGetUpdatesResponse)
  implicit val jf_telegram_get_chat_response: RootJsonFormat[TelegramGetChatResponse] = jsonFormat3(TelegramGetChatResponse)
  implicit val jf_telegram_send_message_response: RootJsonFormat[TelegramSendMessageResponse] = jsonFormat4(TelegramSendMessageResponse)

  // Output model (20 fields)
  implicit val jf_telegram_message: RootJsonFormat[TelegramMessage] = jsonFormat20(TelegramMessage)
}
