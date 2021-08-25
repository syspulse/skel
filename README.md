# skel

Skeletons for RnD Prototypes

1. [skel-http](skel-http) - HTTP Service (+ Kubernetes)
2. [skel-user](skel-user) - UserProfile Service reference
3. [skel-world](skel-world) + [skel-shop](skel-shop) - Services for E-Shop like product
4. [skel-kafka](skel-kafka) - Kafka Processors (Source/Sink)
5. [skel-ingest](skel-ingest) - Data Ingestion with Prometheus telemerty reference
6. [skel-ekm](skel-ekm) - IoT like Service ingestion to InflixDB/Grafana sink
7. [skel-telemetry](skel-telemetry) - Telemetry (Prometheus/InfluxDB/Graphite/Loki/Grafana)
8. [skel-db](skel-db) - DB Related stuff
9. [skel-otp](skel-otp) - OTP Service reference service 
10. [skel-test](skel-test) - Test helpers
11. [skel-crypto](skel-crypto) - Cryptography tools

## Build & Run

__Dev__
```
sbt
~reStart
```

__Fat jar__
```
sbt assembly
```

__Docker__

Support for mulit-platform builds (__amd64__,__arm64__)
```
sbt docker:publish
sbt docker:publishLocal
```

## Configuration

Refer to [skel-http](skel-http) for Configuration details

## Telemetry (Metrics)

Refer to [skel-ingest](skel-ingest) for Prometheus details

----

## Libraries and Credits

1. HTTP: Akka-HTTP [https://doc.akka.io/docs/akka-http/current/index.html](https://doc.akka.io/docs/akka-http/current/index.html)
2. Metrics (Dropwizard): [https://github.com/erikvanoosten/metrics-scala](https://github.com/erikvanoosten/metrics-scala)
3. Metrics (Prometheus): [https://github.com/RustedBones/akka-http-metrics](https://github.com/RustedBones/akka-http-metrics)
4. OpenAPI (Swagger): [https://github.com/swagger-akka-http/swagger-akka-http](https://github.com/swagger-akka-http/swagger-akka-http)
5. UUID: [https://github.com/melezov/scala-uuid](https://github.com/melezov/scala-uuid)
6. Args: [https://github.com/scopt/scopt](https://github.com/scopt/scopt)
7. Configuration: [https://github.com/lightbend/config](https://github.com/lightbend/config)
8. Logging: [http://logback.qos.ch](http://logback.qos.ch)
9. JDBC: Quill [https://getquill.io](https://getquill.io)
10. Kafka: Alpakka [https://github.com/akka/alpakka](https://github.com/akka/alpakka)


Other libraries are referenced in corresponding modules
