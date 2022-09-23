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

