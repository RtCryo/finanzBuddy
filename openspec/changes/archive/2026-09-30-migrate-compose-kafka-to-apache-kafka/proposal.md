# Proposal

## Why

The Compose stack currently combines a Confluent Kafka 8.3.2 image, which requires KRaft, with ZooKeeper-mode configuration. This prevents the local messaging infrastructure from having a consistent, supported startup configuration. Moving to the official Apache Kafka image and KRaft removes the obsolete ZooKeeper dependency and makes the local setup align with current Kafka releases.

## What Changes

- Replace the Confluent Kafka container with a pinned, supported `apache/kafka` image configured for single-node KRaft development use.
- Remove the ZooKeeper service and Kafka's ZooKeeper-era configuration.
- Preserve the container bootstrap address (`kafka:9092`) and align host-run service defaults and documentation with the Compose external listener (`localhost:29092`).
- Document and verify the local Kafka startup and connectivity contract.

## Capabilities

### New Capabilities

- `local-kafka`: Defines the local Compose Kafka broker runtime, KRaft mode, and connectivity expected by application services and local clients.

### Modified Capabilities

None. Existing application-level Kafka publishing requirements remain unchanged.

## Impact

- `docker-compose.yml`: Kafka image, KRaft configuration, listener configuration, health/readiness dependencies, and removal of ZooKeeper.
- Kafka bootstrap defaults in API Gateway configuration and Kafka-related local run/configuration documentation.
- OpenSpec local Kafka infrastructure capability.
- No application API or Kafka event schema changes are intended.
