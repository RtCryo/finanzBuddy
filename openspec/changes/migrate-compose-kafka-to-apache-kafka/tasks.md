# Tasks

## 1. Migrate the local broker to Apache Kafka and KRaft

- [ ] 1.1 Replace the Confluent Kafka and ZooKeeper Compose services with a pinned `apache/kafka` single-node KRaft service, preserving the internal and external bootstrap endpoints; verify `docker compose config` resolves successfully and contains no ZooKeeper service or ZooKeeper-mode settings.
- [ ] 1.2 Configure separate controller, internal, and external listeners plus the required single-node replication settings; verify the broker starts and advertises `kafka:9092` internally and `localhost:29092` externally.
- [ ] 1.3 Add a Kafka-protocol readiness health check and make Kafka-dependent Compose services wait for healthy status; verify dependent containers remain gated until the broker accepts Kafka requests and then start successfully.

## 2. Align host-run client configuration and documentation

- [ ] 2.1 Align the API Gateway host-run Kafka bootstrap default with `localhost:29092` and update its README; verify the documented value matches the Compose external listener and a host-run gateway can connect.
- [ ] 2.2 Review parser-service Kafka defaults and local-run instructions for consistency with `localhost:29092` while retaining `kafka:9092` for Compose; verify the documented local startup path connects to the broker.

## 3. Verify end-to-end local messaging

- [ ] 3.1 Start the full Compose stack and verify Kafka becomes healthy without ZooKeeper and the API Gateway and parser-service start after it.
- [ ] 3.2 Produce and consume a test Kafka record from both a Compose-network client and a host-run client, then upload a supported document and verify its event reaches parser-service.
