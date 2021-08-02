# skel

Skeletons for RnD Prototypes

1. [skel-http](skel-http) - HTTP Service (+ Kubernetes)
2. [skel-user](skel-user) - HTTP Service with DB backend
3. [skel-world](skel-world) + [skel-shop](skel-shop) - Services for E-Shop like product
4. [skel-kafka](skel-kafka) - Kafka Processors (Source/Sink)
5. [skel-telemetry](skel-telemetry) - IoT Service to InflixDB/Grafana sink
6. [skel-grafana](skel-grafana) - docker-compose for Prometheus/InfluxDB/Graphite/Loki/Grafana
7. [skel-db](skel-db) - DB Related stuff

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

Refer to [skel-http](skel-http) for details

----

## Libraries

1. HTTP: Akka-HTTP [https://doc.akka.io/docs/akka-http/current/index.html](https://doc.akka.io/docs/akka-http/current/index.html)
2. Metrics: [https://github.com/erikvanoosten/metrics-scala](https://github.com/erikvanoosten/metrics-scala)
3. OpenAPI (Swagger): [https://github.com/swagger-akka-http/swagger-akka-http](https://github.com/swagger-akka-http/swagger-akka-http)
4. UUID: [https://github.com/melezov/scala-uuid](https://github.com/melezov/scala-uuid)
5. Args: [https://github.com/scopt/scopt](https://github.com/scopt/scopt)
6. Configuration: [https://github.com/lightbend/config](https://github.com/lightbend/config)
7. Logging: [http://logback.qos.ch](http://logback.qos.ch)
8. JDBC: Quill [https://getquill.io](https://getquill.io)
9. Kafka: Alpaka (Akka-Streams)

