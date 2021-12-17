# skel

Skeletons for RnD Prototypes

0. [demo](demo) - Demoable Projlets based on skel components
0. [infra](infra) - Infrastructure (docker,kubernetes)
1. [skel-http](skel-http) - HTTP Service (+ Kubernetes)
2. [skel-user](skel-user) - UserProfile Service reference
3. [skel-world](skel-world) + [skel-shop](skel-shop) - Services for E-Shop like product
4. [skel-kafka](skel-kafka) - Kafka Processors (Source/Sink)
5. [skel-ingest](skel-ingest) - Data Ingestion with Prometheus telemerty reference
6. [skel-telemetry](skel-telemetry) - Telemetry (Prometheus/InfluxDB/Graphite/Loki/Grafana)
7. [skel-db](skel-db) - DB Related stuff
8. [skel-otp](skel-otp) - OTP Service reference service 
9. [skel-test](skel-test) - Test helpers
10. [skel-crypto](skel-crypto) - Cryptography and Ethereum utilities
11. [skel-spark](skel-spark) - Spark tools
12. [skel-datalake](skel-datalake) - Datalake and Warehouses
13. [skel-flow](skel-flow) - DataFlows and Workflows
14. [skel-scrap](skel-scrap) - Scraping pipelines
15. [skel-cli](skel-cli) - Shell command line client
16. [skel-cron](skel-cron) - Cron engine
17. [skel-dashboard](skel-dashboard) - Dashboards (Polynote/Zepellin)

----
## Build & Run

Go to [skel-http](skel-http) for Building and Running generic skel component.

Refer to specific demo for running Kubernetes Deployments or Docker-Compose topologies

----

## Demos

Go to [demo](demo) for demo projlets

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
11. InfluxDB Stream: Alpakka [https://github.com/akka/alpakka](https://github.com/akka/alpakka)
12. InfluxDB-2: [https://github.com/influxdata/influxdb-client-java](https://github.com/influxdata/influxdb-client-java)
13. Geohash: [https://github.com/davidallsopp/geohash-scala](https://github.com/davidallsopp/geohash-scala)
14. Quartz: [http://www.quartz-scheduler.org](http://www.quartz-scheduler.org)
15. Web3: [https://github.com/web3j/web3j](https://github.com/web3j/web3j)
16. BLS: [https://github.com/ConsenSys/teku](https://github.com/ConsenSys/teku)

Other libraries are referenced in corresponding modules
