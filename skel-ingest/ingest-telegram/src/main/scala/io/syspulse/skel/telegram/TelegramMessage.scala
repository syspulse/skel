package io.syspulse.skel.telegram

import io.syspulse.skel.Ingestable

// Main output model for ingested Telegram messages
case class TelegramMessage(
  update_id: Long,
  message_id: Long,
  chat_id: Long,
  chat_type: String,                    // "channel", "group", "supergroup", "private"
  chat_title: Option[String],
  from_id: Option[Long],                // None for channel posts
  from_username: Option[String],
  from_first_name: Option[String],
  date: Long,                           // Unix timestamp
  message_type: String,                 // "text", "photo", "video", "document", "audio", "voice", "unknown"
  text: Option[String],
  caption: Option[String],
  photo_file_ids: Seq[String],          // File IDs for photos (multiple sizes)
  video_file_id: Option[String],
  document_file_id: Option[String],
  document_name: Option[String],
  audio_file_id: Option[String],
  voice_file_id: Option[String],
  forward_from_chat_id: Option[Long],
  reply_to_message_id: Option[Long]
) extends Ingestable

// Telegram Bot API response models
case class TelegramGetUpdatesResponse(
  ok: Boolean,
  result: Seq[TelegramUpdate]
)

case class TelegramUpdate(
  update_id: Long,
  message: Option[TelegramApiMessage],
  channel_post: Option[TelegramApiMessage],
  edited_channel_post: Option[TelegramApiMessage]
)

case class TelegramApiMessage(
  message_id: Long,
  from: Option[TelegramUser],
  chat: TelegramChat,
  date: Long,
  text: Option[String],
  caption: Option[String],
  photo: Option[Seq[TelegramPhotoSize]],
  video: Option[TelegramVideo],
  document: Option[TelegramDocument],
  audio: Option[TelegramAudio],
  voice: Option[TelegramVoice],
  forward_from_chat: Option[TelegramChat],
  reply_to_message: Option[TelegramApiMessage]
)

case class TelegramUser(
  id: Long,
  is_bot: Boolean,
  first_name: String,
  last_name: Option[String],
  username: Option[String]
)

case class TelegramChat(
  id: Long,
  `type`: String,                       // "private", "group", "supergroup", "channel"
  title: Option[String],
  username: Option[String],
  first_name: Option[String],
  last_name: Option[String]
)

case class TelegramPhotoSize(
  file_id: String,
  file_unique_id: String,
  width: Int,
  height: Int,
  file_size: Option[Long]
)

case class TelegramVideo(
  file_id: String,
  file_unique_id: String,
  width: Int,
  height: Int,
  duration: Int,
  thumb: Option[TelegramPhotoSize],
  file_name: Option[String],
  mime_type: Option[String],
  file_size: Option[Long]
)

case class TelegramDocument(
  file_id: String,
  file_unique_id: String,
  thumb: Option[TelegramPhotoSize],
  file_name: Option[String],
  mime_type: Option[String],
  file_size: Option[Long]
)

case class TelegramAudio(
  file_id: String,
  file_unique_id: String,
  duration: Int,
  performer: Option[String],
  title: Option[String],
  file_name: Option[String],
  mime_type: Option[String],
  file_size: Option[Long]
)

case class TelegramVoice(
  file_id: String,
  file_unique_id: String,
  duration: Int,
  mime_type: Option[String],
  file_size: Option[Long]
)

// Response for getChat API call
case class TelegramGetChatResponse(
  ok: Boolean,
  result: Option[TelegramChat],
  description: Option[String] = None
)

// Response for sendMessage API call
case class TelegramSendMessageResponse(
  ok: Boolean,
  result: Option[TelegramApiMessage],
  description: Option[String] = None,
  error_code: Option[Int] = None
)
