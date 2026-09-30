# Design

## Context

See proposal.md for the motivation. The Compose stack is a local-development deployment with one Kafka broker. The API Gateway and parser-service use `kafka:9092` in Compose; host-run clients use the externally published listener. The existing external port is `29092`, while the API Gateway's host default currently points to `9092` and needs alignment.

## Goals / Non-Goals

**Goals:**
- Run the local broker with the official Apache Kafka image in KRaft mode, without ZooKeeper.
- Preserve a stable and reachable bootstrap address for both Compose clients and host-run tools.
- Make Compose startup wait for the broker's Kafka protocol readiness.

**Non-Goals:**
- Define a production Kafka topology, high availability, authentication, or TLS.
- Change Kafka topics, event payloads, outbox delivery semantics, or consumer behavior.
- Add persistence for local Kafka data.

## Decisions

1. **Use the official `apache/kafka` image and pin a supported stable release.** This matches the requested upstream image and avoids tracking a moving `latest` tag. Confluent's `cp-kafka` Community image remains a viable Kafka distribution, but its environment/configuration conventions are not required by this project. `confluent-local` is optimized for local development but is experimental and unnecessary when the upstream image is directly requested.

2. **Use one combined broker/controller KRaft node for Compose only.** A one-node setup fits the local stack and removes ZooKeeper. Combined broker/controller mode is not the production topology; production deployment design remains out of scope. Keep single-node replication settings compatible with the existing one-broker setup.

3. **Use separate internal and external listeners.** Keep `INTERNAL` advertised as `kafka:9092` for the application containers and `EXTERNAL` advertised as `localhost:29092` for host-run clients. Keep the controller listener separate and do not advertise it to Kafka clients. This preserves the current Compose service endpoint while fixing API Gateway's host default to match the published port.

4. **Gate dependent services on broker health, not container start.** Add a Kafka protocol-level health check and use Compose's healthy dependency condition for API Gateway and parser-service. This avoids treating process creation as broker readiness.

5. **Keep plaintext listeners for local development.** The current stack has no local Kafka authentication or TLS contract. Security hardening is not part of this change.

## Risks / Trade-offs

- **Apache and Confluent images may have different entrypoint and environment-variable behavior** -> Configure using the official Apache image's documented KRaft environment variables and validate the rendered Compose configuration plus broker startup.
- **A single combined KRaft node has no broker/controller fault tolerance** -> Scope this topology explicitly to local development; do not use it as production guidance.
- **Kafka may report healthy before application-level topic operations are verified** -> Use a broker API readiness probe and smoke-test produce/consume through both advertised endpoints.
- **Host and container clients need different advertised addresses** -> Test metadata returned through both listeners, not only TCP port reachability.

## Migration Plan

1. Replace the Compose Kafka and ZooKeeper setup with the pinned Apache image and single-node KRaft/listener configuration; add broker health gating for dependent services.
2. Align API Gateway's host-run bootstrap default and Kafka documentation with `localhost:29092`; retain `kafka:9092` in container environment configuration.
3. Validate Compose configuration, start the stack, verify readiness and client connectivity from both networks, and exercise an application event through Kafka.
4. No persistent production data migration is required. If the local setup must be reverted, revert the Compose and related local-configuration changes together; do not mix the new KRaft settings with ZooKeeper-mode configuration.
