# skel-telemetry

Telemetry

## Data Sinks

1. Prometheus
2. InfluxDB
3. Graphite
4. Loki
5. Grafana

## Docker Compose

[docker](docker)

Fix __graphite__ and __influxdb__ links to directories where Dockers will save data

## Ingest

### Ingesting into DynamoDB on dynamo-local

__WARNING__: It is important to set:

AWS_SECRET_KEY=anything
AWS_REGION=localhost 