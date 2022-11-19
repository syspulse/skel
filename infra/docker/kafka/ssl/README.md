# Secure Kafka Cluster

1. SSL/SASL connection for Clients
2. SSL inter-Broker connection

### Create SSL credentials

1. Create Certificat Authority for signing truststores

```
1.ca-create.sh
```

2. Create Broker keystore for Broker SSL keys

Uses Java Keystore (JKS) container

```
2.jks-create.sh
```

3. Sign broker SSL certificate with CA

```
3.ca-sign.sh
```

4. Import signed broker certificate into broker keystore

This could be combined with previous step

```
4.jks-import.sh
```

5. Create client and broker truststores (for broker certificate validation)

This step import __ca.crt__ into trustore

```
5.client-truststore-create.sh
5.broker-truststore-create.sh
```

This step creates 2 JKS files: __client.truststore__ and __broker.truststore__

They will be used for SSL connection

6. Verify SSL connection works

This step must successfully establish SSL connection to broker

```
6.ssl-verify.sh
```

7. Start JVM consumer

Runs consumer based on Kafka Client properties (__client-ssl.properties__)

8. Start kafkacat producer

Runs Producer based on kafkacat

