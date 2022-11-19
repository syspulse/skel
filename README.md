# skel

Skeletons for RnD Prototypes

1. [infra](infra) - Infrastructure (docker,kubernetes)
2. [skel-http](skel-http) - HTTP Service (+ Kubernetes)
3. [skel-user](skel-user) - UserProfile Service reference
4. [skel-world](skel-world) + [skel-shop](skel-shop) - Services for E-Shop like product
5. [skel-kafka](skel-kafka) - Kafka Processors (Source/Sink)
6. [skel-ingest](skel-ingest) - Data Ingestion with Prometheus telemerty reference
7. [skel-telemetry](skel-telemetry) - Telemetry (Prometheus/InfluxDB/Graphite/Loki/Grafana)
8. [skel-db](skel-db) - DB Related stuff
9. [skel-otp](skel-otp) - OTP Service reference service 
10. [skel-test](skel-test) - Test helpers
11. [skel-crypto](skel-crypto) - Cryptography and Ethereum utilities
12. [skel-spark](skel-spark) - Spark tools
13. [skel-datalake](skel-datalake) - Datalake and Warehouses (+ Notebooks)
14. [skel-flow](skel-flow) - DataFlows and Workflows
15. [skel-scrap](skel-scrap) - Scraping pipelines
16. [skel-cli](skel-cli) - Shell command line client
17. [skel-cron](skel-cron) - Cron engine
19. [skel-serde](skel-serde) - Serializers
20. [skel-video](skel-video) - Movie Metadata processors
21. [skel-stream](skel-stream) - Akka Steams 
22. [skel-pdf](skel-pdf) - PDF Utils
23. [skel-enroll](skel-enroll) - Registration Flow
24. [skel-yell](skel-yell) - Auditlog like system service
25. [skel-tag](skel-tag) - Tags searchable service (e.g. labels, tags)

----
## Build & Run

Go to [skel-http](skel-http) for Building and Running generic skel component.

Refer to specific demo for running Kubernetes Deployments or Docker-Compose topologies

----

## Demos

Go to [https://github.com/syspulse/skel-demo](https://github.com/syspulse/skel-demo) for demo projlets

----
## Libraries and Credits

0. Akka: [https://akka.io/](https://akka.io/)
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
11. InfluxDB Stream: Alpakka [https://github.com/akka/alpakka](https://github.com/akka/alpakka)
12. InfluxDB-2: [https://github.com/influxdata/influxdb-client-java](https://github.com/influxdata/influxdb-client-java)
13. Geohash: [https://github.com/davidallsopp/geohash-scala](https://github.com/davidallsopp/geohash-scala)
14. Quartz: [http://www.quartz-scheduler.org](http://www.quartz-scheduler.org)
15. Web3: [https://github.com/web3j/web3j](https://github.com/web3j/web3j)
16. BLS: [https://github.com/ConsenSys/teku](https://github.com/ConsenSys/teku)
17. elastic4s: [https://github.com/sksamuel/elastic4s]

Other libraries are referenced in corresponding modules
