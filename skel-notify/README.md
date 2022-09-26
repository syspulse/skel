# Notificaiton service

Simple Notification Servie

## Run

### Send to a group

```
./run-notify.sh notify stdout:// email://user-1@domain.com email://user-2@domain.com stdout:// Subject Message
```

### Send to AWS SNS arn:

```
./run-notify.sh notify 'sns://arn:aws:sns:eu-west-1:649502643044:notify-topic' Subject Body  
```

### Send to email (via STMP)

```
./run-notify.sh notify --smtp.uri="smtp://$SMTP_HOST:25/$SMTP_USER@$SMTP_PASS" notify 'email://snmp/email-1@domain.io' Subject Body
```

### Send to Websocket

```
./run-notify.sh server+notify
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
