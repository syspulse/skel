# Telegram Source and Sink for Skel Ingest

A Telegram implementation for the Skel ingest framework that supports both **Bot API** and **User API (MTProto)** for reading and writing messages to Telegram channels and groups.

## Two APIs Available

### 1. Bot API (Default - Already Implemented)
- ✅ Easy setup with bot token from @BotFather
- ✅ Suitable for public bots and automation
- ⚠️ Limited by Privacy Mode in groups
- ⚠️ Cannot access message history before bot joined

### 2. User API / MTProto (New - Kotlogram)
- ✅ Connect as a regular Telegram user
- ✅ **No Privacy Mode restrictions** - see all messages in groups you're a member of
- ✅ **Full message history access**
- ✅ Access to any chat you're a member of
- ⚠️ Requires phone number + verification code
- ⚠️ More complex initial setup

**When to use User API:**
- You need to monitor private groups without making the bot admin
- You want to access message history
- You're archiving/monitoring channels you're already a member of
- Privacy Mode cannot be disabled in target groups

## Features

### Source (Reading Messages)
- ✅ Long polling via Telegram Bot API `getUpdates` endpoint
- ✅ Support for both channels (channel_post) and groups (message)
- ✅ **Automatic chat type detection** - automatically detects if a chat is a group or channel
- ✅ All message types: text, photo, video, document, audio, voice
- ✅ Automatic offset tracking to resume from last position
- ✅ Deduplication with circular buffer
- ✅ Configurable polling frequency and timeout
- ✅ Environment variable support for bot token
- ✅ Multiple chat monitoring
- ✅ Intelligent warnings for Privacy Mode (groups) and admin requirements (channels)

### Sink (Writing Messages)
- ✅ Send messages to channels and groups via Telegram Bot API `sendMessage` endpoint
- ✅ Automatic message truncation (Telegram 4096 char limit)
- ✅ JSON object auto-conversion to text messages
- ✅ Support for both numeric chat IDs and chat names
- ✅ Error handling and logging

## URI Format

```
telegram://BOT_TOKEN@chat_id1,chat_id2?freq=30000&timeout=30000&allowed_updates=message,channel_post&buffer=1000&max=100
```

### Parameters

| Parameter | Description | Default | Example |
|-----------|-------------|---------|---------|
| `BOT_TOKEN` | Telegram bot token (supports `${TELEGRAM_BOT_TOKEN}`) | Required | `123456:ABC-DEF` |
| `chat_id1,chat_id2` | Comma-separated chat IDs to monitor | Required | `-1001234567890` |
| `freq` | Polling frequency in **milliseconds** | 30000 | `15000` |
| `timeout` | Long polling timeout in **milliseconds** (API max: 90000ms/90s) | 30000 | `20000` |
| `allowed_updates` | Update types: "message", "channel_post" | both | `message` |
| `buffer` | Deduplication buffer size | 1000 | `500` |
| `max` | Max updates per request | 100 | `50` |

**Note:** All time values are in milliseconds. The timeout is converted to seconds internally when calling the Telegram Bot API (which expects seconds).

### Chat ID Format

**You can now use EITHER group names OR numeric IDs!**

- **Group Names**: `telegram://skel-telegram` (name will be auto-resolved)
- **Numeric IDs**: `telegram://-5111202365` (direct chat ID)
- **Mix both**: `telegram://skel-telegram,-1001234567890,my-other-group`

**Numeric ID formats:**
- **Channels**: Use format `-100{channel_id}` (e.g., `-1001234567890`)
- **Groups**: Group chat ID (negative numbers, e.g., `-5111202365`)
- **Private**: Private chat IDs (typically positive numbers)

**How name filtering works:**
1. Bot receives all messages from groups/channels where it's a member
2. Filters messages by matching EITHER the chat ID OR the chat title
3. No pre-resolution needed - just works automatically!

**Examples:**
- `telegram://skel-telegram` - Accepts messages from group named "skel-telegram"
- `telegram://-5111202365` - Accepts messages from chat with ID -5111202365
- `telegram://skel-telegram,-1001234567890` - Accepts from BOTH (name and ID)

**To get a chat ID manually:**
1. Add your bot to the channel/group
2. Send a message
3. Run: `./get-chat-id.sh YOUR_BOT_TOKEN`
4. Or check: `https://api.telegram.org/bot{TOKEN}/getUpdates`

---

# User API (MTProto) Setup - NEW!

If you need to connect as a regular user instead of a bot, use the Kotlogram-based User API.

## Prerequisites for User API

1. **Get API Credentials from Telegram**:
   - Go to https://my.telegram.org/apps
   - Login with your phone number
   - Create a new application
   - Save your `api_id` and `api_hash`

2. **Set Environment Variables**:
   ```bash
   export TELEGRAM_API_ID="12345678"
   export TELEGRAM_API_HASH="0123456789abcdef0123456789abcdef"
   export TELEGRAM_PHONE="+1234567890"  # Your phone number
   ```

3. **First-Time Authentication**:
   - On first run, you'll receive a verification code via Telegram
   - Set it as: `export TELEGRAM_CODE="12345"`
   - Session is saved and reused for subsequent runs
   - If 2FA enabled: `export TELEGRAM_PASSWORD="your_password"`

## Usage Examples - User API

### Test Authentication (with Bloop)

```bash
cd ingest-telegram

# Set environment variables
export TELEGRAM_API_ID="12345678"
export TELEGRAM_API_HASH="0123456789abcdef0123456789abcdef"
export TELEGRAM_PHONE="+1234567890"

# Run test (will prompt for code on first run)
./run-kotlogram.sh

# Or run directly with bloop
cd ..
bloop run ingest_telegram --main io.syspulse.skel.telegram.AppKotlogram
```

**Interactive Authentication:**
- First run: Will prompt for verification code from Telegram app
- Subsequent runs: Uses saved session (no prompt)
- 2FA: Will prompt for password if enabled

**Non-Interactive (CI/CD):**
```bash
export TELEGRAM_CODE="12345"       # Code from Telegram
export TELEGRAM_PASSWORD="secret"  # If 2FA enabled
./run-kotlogram.sh
```

This will authenticate and list your first 10 dialogs.

### Read Messages as User

```scala
import io.syspulse.skel.telegram.TelegramUserClient

// Implement the client
object MyUserClient extends TelegramUserClient {
  override def getApiId(): Int = sys.env("TELEGRAM_API_ID").toInt
  override def getApiHash(): String = sys.env("TELEGRAM_API_HASH")
  override def getPhoneNumber(): String = sys.env("TELEGRAM_PHONE")
}

// Create authenticated client
val client = MyUserClient.createClient().get

// Get your dialogs (chats)
val dialogs = MyUserClient.getDialogs(client, 100)

// Stream messages from chats
val messageStream = MyUserClient.source(
  channels = Set.empty,  // Empty = monitor first dialog
  freq = 5000L,          // Poll every 5 seconds
  max = 100              // Max 100 messages per poll
)

// Close client when done
client.close()
```

## User API vs Bot API Comparison

| Feature | Bot API | User API (MTProto) |
|---------|---------|-------------------|
| **Setup** | Easy (bot token) | Moderate (phone + code) |
| **Privacy Mode** | Blocked by it | ✅ Not affected |
| **Message History** | Only new messages | ✅ Full history access |
| **Group Access** | Must be added as bot | ✅ Any group you're in |
| **Admin Required** | Yes (for channels) | No |
| **Rate Limits** | ~30 req/sec | ~20 req/sec |
| **Terms of Service** | Bot TOS | User TOS (no spam!) |
| **Use Case** | Public bots | Personal monitoring |

## Important Notes for User API

⚠️ **Telegram Terms of Service:**
- User API is for personal use and monitoring
- Do NOT use for spam or mass messaging
- Do NOT automate actions at scale
- Ensure compliance with [Telegram ToS](https://telegram.org/tos)

✅ **Good Use Cases:**
- Personal message archiving
- Monitoring groups you're a member of
- Research and data collection (with consent)
- Bot development and testing

❌ **Bad Use Cases:**
- Spam or unsolicited messages
- Mass scraping of user data
- Automated engagement at scale

## Kotlogram Implementation Details

The User API implementation uses [Kotlogram](https://github.com/badoualy/kotlogram), a Kotlin/Java MTProto library:

- **Library**: `com.github.badoualy:kotlogram:1.0.0-RC2`
- **Protocol**: MTProto (Telegram's native protocol)
- **Session Storage**: File-based (`telegram_session.dat`)
- **Authentication**: Phone + SMS code + optional 2FA

---

# Bot API Documentation

## Prerequisites

1. **Create a Telegram Bot**:
   ```bash
   # Talk to @BotFather on Telegram
   /newbot
   # Follow instructions to get your bot token
   ```

2. **Add Bot to Channel/Group**:
   - For channels: Add bot as administrator
   - For groups: Add bot as member

3. **Set Environment Variable** (optional):
   ```bash
   export TELEGRAM_BOT_TOKEN="123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11"
   ```

## Usage Examples

### 1. Read from a Group by NAME

```bash
export TELEGRAM_BOT_TOKEN="your-bot-token"

./run-ingest.sh \
  -f 'telegram://skel-telegram' \
  -o stdout://
```

Just use the group name - no need to know the chat ID!

### 2. Read from Multiple Groups (Names and IDs)

```bash
./run-ingest.sh \
  -f 'telegram://skel-telegram,my-other-group,-1001234567890' \
  -o file://messages.json
```

Mix names and numeric IDs freely!

### 3. Read from a Channel by ID

```bash
./run-ingest.sh \
  -f 'telegram://${TELEGRAM_BOT_TOKEN}@-1001234567890' \
  -o stdout://
```

### 4. Custom Polling Frequency (15 seconds) and Timeout (20 seconds)

```bash
./run-ingest.sh \
  -f 'telegram://${TELEGRAM_BOT_TOKEN}@-100123?freq=15000&timeout=20000' \
  -o stdout://
```

### 4. Only Group Messages (no channel posts)

```bash
./run-ingest.sh \
  -f 'telegram://${TELEGRAM_BOT_TOKEN}@-100123?allowed_updates=message' \
  -o stdout://
```

### 5. Write to Kafka

```bash
./run-ingest.sh \
  -f 'telegram://${TELEGRAM_BOT_TOKEN}@-100123' \
  -o 'kafka://localhost:9092?topic=telegram-messages'
```

### 6. Write to Elasticsearch

```bash
./run-ingest.sh \
  -f 'telegram://${TELEGRAM_BOT_TOKEN}@-100123' \
  -o 'elastic://localhost:9200/telegram/_doc'
```

## Sink Usage Examples (Writing to Telegram)

The Telegram sink allows you to **send messages** to Telegram channels and groups. Any data object is automatically converted to JSON text and sent as a message.

### Chat Identification for Sink

The sink supports **three ways** to specify the target chat:

1. **Numeric ID** (most reliable): `telegram://-1001234567890`
   - Always works, fastest
   - Find using: `./get-chat-id.sh`
   - Channel IDs start with `-100` (e.g., `-1001234567890`)
   - Group IDs are negative (e.g., `-5111202365`)

2. **Username**: `telegram://@channel_name`
   - Works if channel/group has a public username
   - Must include `@` prefix

3. **Chat Title** (auto-resolved): `telegram://My Channel Name`
   - Automatically resolved to numeric ID via `getUpdates` API
   - ⚠️ **Requires recent messages** in the chat (Telegram API limitation)
   - If resolution fails, send a message to the chat first

**How Title Resolution Works:**
- Telegram Bot API has no direct "title → ID" lookup method
- We use `getUpdates` to parse recent messages and extract chat titles + IDs
- This is the standard approach per [Telegram Bot API documentation](https://core.telegram.org/bots/api)

### Prerequisites for Sink

1. Bot must be added to the target group/channel
2. **For channels**: Bot must be an **administrator** with "Post Messages" permission
3. **For groups**: Bot must be a member
4. **For title resolution**: At least one recent message must exist in the chat

### 1. Send JSON Data to a Group

```bash
export TELEGRAM_BOT_TOKEN="your-bot-token"

# Read from file and send each line to Telegram
./run-ingest.sh \
  -f file://data.json \
  -o 'telegram://${TELEGRAM_BOT_TOKEN}@-5111202365'
```

### 2. Send Messages to a Channel

```bash
# Send to a channel (use -100 prefix format)
./run-ingest.sh \
  -f stdin:// \
  -o 'telegram://${TELEGRAM_BOT_TOKEN}@-1001234567890'
```

### 3. Forward Messages from One Group to Another

```bash
# Read from one Telegram group and forward to another
./run-ingest.sh \
  -f 'telegram://${TELEGRAM_BOT_TOKEN}@skel-source' \
  -o 'telegram://${TELEGRAM_BOT_TOKEN}@skel-destination'
```

### 4. Send Kafka Messages to Telegram

```bash
# Read from Kafka and post to Telegram
./run-ingest.sh \
  -f 'kafka://localhost:9092?topic=alerts' \
  -o 'telegram://${TELEGRAM_BOT_TOKEN}@-5111202365'
```

### 5. Monitor HTTP Endpoint and Post to Telegram

```bash
# Poll HTTP endpoint and send updates to Telegram
./run-ingest.sh \
  -f 'clock://30s:http://api.example.com/status' \
  -o 'telegram://${TELEGRAM_BOT_TOKEN}@monitoring-channel'
```

### 6. Send Test Message

```bash
echo '{"text":"Hello from pipeline!"}' | ./run-ingest.sh \
  -f stdin:// \
  -o 'telegram://${TELEGRAM_BOT_TOKEN}@-5111202365'
```

## Output Format (Source)

Messages are output as newline-delimited JSON:

```json
{
  "update_id": 123456789,
  "message_id": 42,
  "chat_id": -1001234567890,
  "chat_type": "channel",
  "chat_title": "My Tech Channel",
  "from_id": null,
  "from_username": null,
  "from_first_name": null,
  "date": 1703001234,
  "message_type": "text",
  "text": "Hello, world!",
  "caption": null,
  "photo_file_ids": [],
  "video_file_id": null,
  "document_file_id": null,
  "document_name": null,
  "audio_file_id": null,
  "voice_file_id": null,
  "forward_from_chat_id": null,
  "reply_to_message_id": null
}
```

### Message Types

- **text**: Regular text messages
- **photo**: Photo messages (includes `photo_file_ids` array and optional `caption`)
- **video**: Video messages (includes `video_file_id` and optional `caption`)
- **document**: Document/file messages (includes `document_file_id` and `document_name`)
- **audio**: Audio messages (includes `audio_file_id`)
- **voice**: Voice messages (includes `voice_file_id`)
- **unknown**: Other message types

### Chat Types

- **channel**: Public or private channel
- **group**: Regular group
- **supergroup**: Supergroup
- **private**: Private chat

## Automatic Chat Type Detection

When you provide numeric chat IDs, the system **automatically detects** whether each chat is a group or channel using the Telegram Bot API `getChat` method:

```
Starting Telegram source...
Detecting chat types for numeric IDs...
✓ Chat 'My Channel' (-1001234567890) is a channel
✓ Chat 'My Group' (-5111202365) is a supergroup
Detected: 1 channel(s), 1 group(s), 0 private chat(s)
💡 Recommended allowed_updates: message, channel_post (currently: message, channel_post)
ℹ️  For groups/supergroups:
   - Make sure Privacy Mode is DISABLED in @BotFather
   - Otherwise bot will only see commands and mentions
ℹ️  For channels:
   - Bot must be added as an ADMINISTRATOR
   - Otherwise it won't receive channel_post updates
```

### Benefits

1. **Validation**: Confirms bot has access to each chat before starting
2. **Smart Recommendations**: Suggests optimal `allowed_updates` configuration
3. **Helpful Warnings**: Reminds about Privacy Mode and admin requirements
4. **Clear Logging**: Shows exactly what type each chat is

### How It Works

1. Bot extracts numeric IDs from URI (e.g., `-1001234567890`, `-5111202365`)
2. Calls `getChat` API for each ID to retrieve chat type
3. Logs detected types: `channel`, `group`, `supergroup`, or `private`
4. Recommends `allowed_updates` based on detected types:
   - **Only channels**: `channel_post`
   - **Only groups**: `message`
   - **Both**: `message, channel_post` (default)

## Architecture

### Source (Reading)
```
Pipeline.scala (telegram:// routing)
    ↓
Flows.scala (fromTelegram method)
    ↓
FromTelegram class (URI parsing)
    ↓
TelegramClient trait (HTTP client + polling logic)
    ├─ getChat (detect chat types)
    └─ getUpdates (poll for messages)
    ↓
Telegram Bot API
```

### Sink (Writing)
```
Pipeline.scala (telegram:// routing)
    ↓
Flows.scala (toTelegram method)
    ↓
ToTelegram class (URI parsing + sink logic)
    ↓
TelegramClient trait (HTTP client)
    └─ sendMessage (post messages)
    ↓
Telegram Bot API
```

### Key Components

1. **TelegramURI** (`skel-core`): Parses URI and extracts parameters
2. **TelegramMessage**: Data model for output messages
3. **TelegramJson**: Spray-json formatters for serialization
4. **TelegramClient**: HTTP client with polling and sending implementation
   - `getChat`: Detect chat types
   - `getUpdates`: Poll for new messages (source)
   - `sendMessage`: Send messages (sink)
5. **FromTelegram**: Source implementation (reading messages)
6. **ToTelegram**: Sink implementation (writing messages)

### Offset Tracking

The source maintains a stateful offset to track the last processed update:

1. Starts with offset = 0
2. After each poll, offset = max(update_ids) + 1
3. Offset persists during stream lifecycle
4. Resets to 0 on restart (no file persistence)

### Deduplication

Circular buffer prevents duplicate messages:

1. Tracks seen `update_id` values in a Set
2. Buffer size configurable via `buffer` parameter
3. When buffer exceeds limit, oldest 10% are evicted
4. Default buffer size: 1000 messages

## Limitations

- **No webhook support**: Uses long polling only
- **No offset persistence**: Offset resets on restart
- **No file download**: Stores file_id only, not actual files
- **No inline queries**: Only channel posts and messages

## Telegram Bot API References

- [Bot API Documentation](https://core.telegram.org/bots/api)
- [getUpdates Method](https://core.telegram.org/bots/api#getupdates)
- [Update Object](https://core.telegram.org/bots/api#update)
- [Message Object](https://core.telegram.org/bots/api#message)

## Troubleshooting

### Quick Diagnostic Tool

Run the diagnostic script to automatically check your bot configuration:

```bash
export TELEGRAM_BOT_TOKEN="your-bot-token"
cd ingest-telegram
./diagnose-telegram.sh
```

This will:
- ✓ Verify bot token is valid
- ✓ Show recent updates
- ✓ Identify chat IDs
- ✓ Detect Privacy Mode issues
- ✓ Provide specific fixes

### Common Issues

#### 1. Bot doesn't receive GROUP messages

**Problem:** Bot only sees messages that start with `/` or mention the bot

**Cause:** Privacy Mode is enabled (Telegram default for groups)

**Solution:**
```bash
# Disable Privacy Mode:
1. Go to @BotFather on Telegram
2. Send: /mybots
3. Select your bot
4. Go to: Bot Settings → Group Privacy
5. Turn OFF Privacy Mode
6. Wait 5-10 minutes for changes to propagate
```

**Verify:** Send a regular message in your group. Bot should see it.

#### 2. Wrong chat ID

**Problem:** No messages received, or filtered out

**Check logs:** Look for messages like:
```
Filtering out chat_id=-123456 (not in whitelist: Set(-789012))
```

**Solution:**
```bash
# Get correct chat ID:
curl "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getUpdates" | jq '.result[].message.chat'

# Or use diagnostic script:
./diagnose-telegram.sh
```

**Note:**
- Group/supergroup IDs are negative numbers
- Channel IDs start with `-100` prefix: `-1001234567890`
- Private chat IDs are positive numbers

#### 3. Bot not added to chat

**Symptoms:**
- No updates in `/getUpdates`
- Logs show: "Received 0 updates from Telegram API"

**Solution:**
```bash
# Add bot to group/channel:
1. Open your group/channel
2. Click "Add members" or "Administrators"
3. Search for your bot username
4. Add the bot

# For channels: Bot must be ADMIN
# For groups: Bot can be regular member (if Privacy Mode is OFF)
```

#### 4. Bot receives updates but filters them out

**Symptoms:**
- Logs show: "Received X updates but parsed 0 messages"
- Filtering warnings in logs

**Solution:**
```bash
# Option 1: Monitor ALL chats (remove chat ID filter)
telegram://${TELEGRAM_BOT_TOKEN}@

# Option 2: Use correct chat ID from logs
telegram://${TELEGRAM_BOT_TOKEN}@-1001234567890
```

#### 5. "401 Unauthorized" error

**Cause:** Invalid bot token

**Solution:**
```bash
# Verify token:
curl "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getMe"

# Should return bot info like:
# {"ok":true,"result":{"id":123456789,"is_bot":true,"first_name":"MyBot",...}}
```

#### 6. Messages arrive late or not at all

**Causes:**
- Offset moved too far forward
- Deduplication buffer issues
- Network problems

**Solutions:**
```bash
# Restart with fresh offset (restart the application)

# Increase max updates per request
telegram://${TELEGRAM_BOT_TOKEN}@-100123?max=100

# Increase timeout for long polling
telegram://${TELEGRAM_BOT_TOKEN}@-100123?timeout=60000

# Check logs for API errors
```

#### 7. Only seeing old messages

**Cause:** Offset stuck in the past

**Solution:**
- Restart application (offset resets to latest)
- Or manually call `/getUpdates` with offset=-1 to skip to latest:
```bash
curl "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getUpdates?offset=-1"
```

### Debug Logging

Enable debug logging to see exactly what's happening:

```bash
# Set log level to DEBUG in your logback.xml or application.conf
<logger name="io.syspulse.skel.telegram" level="DEBUG"/>
```

You'll see detailed logs like:
```
[DEBUG] Update 123456: chat_id=-1001234567890, type=supergroup, title=My Group
[INFO]  ✓ Accepting message: update_id=123456, chat=My Group(-1001234567890), type=text, preview='Hello world'
[WARN]  Filtering out chat_id=-999999, title='Other Group' (not in whitelist: Set(-1001234567890))
```

### Rate Limiting

**Telegram limits:** ~30 requests/second

**If you see 429 errors:**
```bash
# Increase polling frequency (reduce requests/second)
telegram://${TELEGRAM_BOT_TOKEN}@-100123?freq=60000  # Poll every 60 seconds

# Increase long polling timeout (fewer requests, more efficient)
telegram://${TELEGRAM_BOT_TOKEN}@-100123?timeout=60000  # 60 second timeout
```

### Still Not Working?

1. **Run diagnostic script**: `./diagnose-telegram.sh`
2. **Check bot username**: Must be correct (from @BotFather)
3. **Test with simple curl**:
   ```bash
   # This should show recent messages:
   curl "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getUpdates?limit=10"
   ```
4. **Verify bot permissions**: In group settings, check bot has right permissions
5. **Wait after Privacy Mode change**: Can take 5-10 minutes to apply
6. **Try different group**: Create test group to isolate the issue

## Development

### Build

```bash
sbt "project ingest_telegram" compile
```

### Test

```bash
# Run URI parsing tests
scala-cli test-telegram-uri.sc

# Test with real bot (requires valid token)
./run-ingest.sh -f 'telegram://${TELEGRAM_BOT_TOKEN}@YOUR_CHAT_ID' -o stdout://
```

### Dependencies

No additional dependencies required - uses existing:
- `akka-stream`
- `akka-http`
- `spray-json`
- `scala-logging`

## License

Part of Skel framework
