# Notificaiton service

Simple Notification Servie

## Run

## Server

```
./run-notify.sh server

./notify-create.sh "stdout://,"ws://" Subject Message
```

Run locally as docker:

```
SMTP_HOST=mail.server.org:465 SMTP_USER=user SMTP_PASS=pass SMTP_FROM=no-reply@mail.another-server.org OPT=-Dgod ../tools/run-docker.sh server
```

## Send to a group

```
./run-notify.sh notify stdout:// email://user-1@domain.com email://user-2@domain.com stdout:// Subject Message
```

## Send to AWS SNS arn:

```
./run-notify.sh notify 'sns://arn:aws:sns:eu-west-1:649502643044:notify-topic' Subject Body  
```

## Send to email (via STMP)

__NOTE__: it is important to specify `from` email for valid DNS domain (otherwise it may timeout requesting that domain) !

```
./run-notify.sh notify --smtp.from=test@syspulse.io --smtp.uri="smtp://$SMTP_HOST:465/$SMTP_USER/$SMTP_PASS" notify 'email://smtp/email-1@domain.io' Subject Body
```

SMTP uri:

`smtp://server:port/user/pass/tls`

By default if `tls` is not specified, port will select the following TLS type:

| port  |  TLS type |  `tls`|
|-------|-----------|-------|
|  465  |  SSL      |  tls or ssl |  
|  567  |  STARTTLS |  starttls |

TLS by default: `smtp://server:465/user/pass`

TLS enforced: `smtp://server:1465/user/pass/tls`

STARTTLS enforced: `smtp://server:25/user/pass/starttls`

STARTTLS by default: `smtp://server:567/user/pass`

## Send email from AWS SES

1. Configure DKIM
2. Enabled Custom MAIL FROM : `mail.ses-domain.org`
3. Enable SPIF: `mail.ses-domain.org TXT "v=spf1 include:amazonses.com ~all"` 


```
SMTP_HOST=mail.server.org:465 SMTP_USER=user SMTP_PASS=pass ./run-notify.sh notify --smtp.from=admin@mail.ses-domain.org notify email://user@gmail.com
```

## Send to Websocket

```
./run-notify.sh server
```

Connect WS clients:
```
wscat --connect ws://localhost:8080/api/v1/notify/ws
```

Send (type in stdin while notify is running)
```
ws:// Title Message
```

Connect WS clients to specific topic:
```
wscat --connect ws://localhost:8080/api/v1/notify/ws/topic1
```

Send (type in stdin while notify is running)
```
ws://topic1 Title "Message for Topic-1"
```

## Send to Telegram Channel

1. Create Telegram bot

https://t.me/BotFather

```
/newbot
```

2. Save Bot API key to $BOT_KEY

3. Create Channel and add new bot to it

4. Get Channgel ID

```
curl https://api.telegram.org/bot$BOT_KEY/getUpdates |jq . > bot-update.json
cat bot-update.json |jq .result[].message.chat.id
```

__NOTE__: How disrespectful you have to be to design API like this !?...

5. Run notifier to Channel Id

```
source telegram-test.env

./run-notify.sh notify "tel://$CHANNEL_ID/$BOT_KEY" Title Message
```

## Push Notification to User

__ATTENTION__: uri is `api/v1/notify/user` 

Connect User clients:

```
wscat --connect ws://localhost:8080/api/v1/notify/user/00000000-0000-0000-1000-000000000001
wscat --connect ws://localhost:8080/api/v1/notify/user/00000000-0000-0000-1000-000000000002
```

Notify specific client

```
./run-notify.sh client notify user://00000000-0000-0000-1000-000000000001 Alarm Attention!! 10
./run-notify.sh client notify user:// Alarm Attention!! 10 00000000-0000-0000-1000-000000000001
```

Notify ALL connected clients (via websocket)

```
./run-notify.sh client notify user:// Alarm Attention!!! 100 sys.all
./run-notify.sh client notify user://sys.all Alarm Attention!!! 100
```

## Emebdded Destination

Examples 
- Event over http: `event://http://POST:ASYNC@@localhost:8300`
- Event over Kafka: `event://kafka://localhost:9092/events`
