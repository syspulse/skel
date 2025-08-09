# Kafka

Most of the kafka is in [skel-ingest] (Flow)

### Ordering

https://www.baeldung.com/kafka-message-ordering
https://medium.com/codex/kafka-what-is-2-generals-problem-and-can-it-affect-my-kafka-producer-2a621d4e1fbd

### Large Messages

https://forum.confluent.io/t/kafka-where-can-i-set-max-request-size-parameter/3243

https://repost.aws/articles/ARfShOOzvBSra6UwgWOz9GXg/handling-large-messages-in-amazon-managed-streaming-for-apache-kafka-msk

```
 kafka-configs.sh --bootstrap-server broker:9092 --alter --entity-type topics --entity-name topic-1 --add-config max.message.bytes=3000000
```

Producer:

```

```

