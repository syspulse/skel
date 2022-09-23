# Notificaiton service

Simple Notification Servie

## Run

Send to a group

```
./run-notify.sh notify stdout:// email://user-1@domain.com email://user-2@domain.com stdout:// Subject Message
```


Send to AWS SNS arn:

```
./run-notify.sh notify 'sns://arn:aws:sns:eu-west-1:649502643044:notify-topic' Subject Body  
```

Sent to email (via STMP)

```
./run-notify.sh --smtp.uri="smtp://$SMTP_HOST:25/$SMTP_USER@$SMTP_PASS" notify 'email://snmp/email-1@domain.io' Subject Body
```
