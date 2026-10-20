# Local Kafka Specification

## Purpose

Defines the local Kafka broker environment used by the Compose stack and the connectivity contract for containerized services and host-run development tools.

## Requirements

### Requirement: Local Kafka runs without ZooKeeper
The local Compose environment SHALL provide a single-node Kafka broker using KRaft metadata management and SHALL NOT require a ZooKeeper service.

#### Scenario: Start the local messaging infrastructure
- **WHEN** a developer starts the Kafka service through Docker Compose
- **THEN** the broker starts in KRaft mode without a ZooKeeper container

### Requirement: Kafka is reachable from containers and the host
The local Kafka broker SHALL advertise a container-network endpoint at `kafka:9092` and a host endpoint at `localhost:29092` so clients connecting through each listener receive a reachable broker address.

#### Scenario: Containerized services connect to Kafka
- **WHEN** the API Gateway or parser-service connects using `kafka:9092`
- **THEN** it can produce or consume Kafka records over the Compose network

#### Scenario: Host-run development service connects to Kafka
- **WHEN** a service or Kafka tool runs on the host and connects using `localhost:29092`
- **THEN** it can produce or consume Kafka records through the published external listener

### Requirement: Application startup waits for Kafka readiness
The Compose environment SHALL report Kafka healthy only after it accepts Kafka protocol requests, and application services that depend on Kafka SHALL wait for that healthy state before starting.

#### Scenario: Kafka is still initializing
- **WHEN** Docker Compose starts the stack and the Kafka broker has not yet become ready
- **THEN** Kafka-dependent application services remain waiting rather than starting against an unavailable broker

#### Scenario: Kafka becomes ready
- **WHEN** the broker begins accepting Kafka protocol requests
- **THEN** Kafka is marked healthy and Kafka-dependent application services may start

## Related implementation and history

- Documentation: [deployment and Kafka map](../../../docs/README.md#subsystem-map), [architecture](../../../docs/ARCHITECTURE.md)
- Implementation: [`docker-compose.yml`](../../../docker-compose.yml), [`docker-compose.override.yml`](../../../docker-compose.override.yml), [`KafkaConfig.java`](../../../parser-service/src/main/java/com/finanzbuddy/parser/config/KafkaConfig.java)
- History: [Compose Kafka migration](../../changes/archive/2026-09-30-migrate-compose-kafka-to-apache-kafka/)
