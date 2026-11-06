# Task 6 - Event-driven architecture with Apache Kafka

## Objective and separation from Task 5

One request to **Client Service** publishes **one Kafka record**. **Storage Service** and the independent **Audit Service** both subscribe to `client.events`, with **different Kafka consumer group IDs**. Each consumer logs the same `eventId`. Kafka delivers the record to both groups independently; the Client Service does not invoke either service for this new event endpoint.

```text
POST /api/client/events
           |
           v
     Client Service :8081
           | Kafka producer (key=clientId)
           v
      Kafka broker :9092 internal
        topic: client.events
           |                  |
           v                  v
 Storage Service         Audit Service
 group: storage-         group: audit-
 service-client-events   service-client-events
           |                  |
           v                  v
 KAFKA_STORAGE_RECEIVED  KAFKA_AUDIT_RECEIVED
```

**Important:** Task 5 remains separate: the existing `POST /api/messages` endpoint continues to save in PostgreSQL and trigger its RabbitMQ email notification. Kafka does not replace RabbitMQ or the existing HTTP/Feign APIs. The Kafka listener in Storage Service logs events only; it does not create a second database message or send an email.

## Event contract

POST JSON to `http://localhost:8081/api/client/events`:

```json
{
  "clientId": "customer-42",
  "action": "REGISTERED"
}
```

Client Service publishes the following JSON on topic `client.events` (the event ID and timestamp are generated for each request):

```json
{
  "eventId": "example-unique-uuid",
  "clientId": "customer-42",
  "action": "REGISTERED",
  "occurredAt": "2026-11-06T12:00:00Z"
}
```

A successful broker acknowledgment results in HTTP **202 Accepted** with the event payload. A blank `clientId` or `action` results in HTTP **400 Bad Request**; a Kafka publication failure returns **503 Service Unavailable**. HTTP 202 means the **broker accepted the record**, not that both consumers have already processed it.

## Demo on Windows (existing repository)

**Prerequisites:** Docker Desktop with Linux containers. The existing repository is `D:\dev\microservices-lab`. Keep its current `.env` and its `DB_PASSWORD`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, and `NOTIFICATION_RECIPIENT`. The existing optional Compose override lives at `D:\dev\microservices-lab.local.yml` and currently provides a local Feign read timeout. **Do not overwrite or delete it.**

1. From PowerShell, start the complete stack. The first Kafka image download and Java rebuild can be slow:

   ```powershell
   cd D:\dev\microservices-lab
   docker compose -f .\docker-compose.yml -f ..\microservices-lab.local.yml config --quiet
   docker compose -f .\docker-compose.yml -f ..\microservices-lab.local.yml up -d --build
   docker compose -f .\docker-compose.yml -f ..\microservices-lab.local.yml ps
   ```

   Expect `kafka` to become **healthy**, and `client-service`, `storage-service`, `audit-service`, `email-service`, `rabbitmq`, `mailpit`, and `postgres-db` to run. No new `.env` variables are required for Kafka.

2. Send **one** test event:

   ```powershell
   $event = Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/api/client/events' `
     -ContentType 'application/json' `
     -Body '{"clientId":"customer-42","action":"REGISTERED"}'
   $event | ConvertTo-Json
   ```

   Copy the returned `eventId`. The new endpoint publishes to Kafka only and does not use Feign, PostgreSQL, or RabbitMQ.

3. Verify that **both separate consumer services** log the **same `eventId`**:

   ```powershell
   docker logs --since 5m client-service 2>&1 | Select-String 'KAFKA_PUBLISHED'
   docker logs --since 5m storage-service 2>&1 | Select-String 'KAFKA_STORAGE_RECEIVED'
   docker logs --since 5m audit-service 2>&1 | Select-String 'KAFKA_AUDIT_RECEIVED'
   ```

   The output should contain three log lines with the same `eventId` (one publish, two independent consumptions). For exact matching, optionally run:

   ```powershell
   docker logs --since 5m storage-service 2>&1 | Select-String $event.eventId
   docker logs --since 5m audit-service 2>&1 | Select-String $event.eventId
   ```

4. Inspect group membership inside the Kafka container (two **different groups** subscribed to the same topic):

   ```powershell
   docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh `
     --bootstrap-server localhost:9092 --describe --group storage-service-client-events
   docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh `
     --bootstrap-server localhost:9092 --describe --group audit-service-client-events
   ```

   The Kafka image uses **KRaft** in a local single-node Docker setup (no ZooKeeper). Container-to-container Kafka traffic uses `kafka:9092`; an optional host-only listener is at `localhost:9094`. These are development-only plaintext listeners.

5. To verify that Task 5 still works, open `http://localhost:8025` (Mailpit) and, if needed, use the existing Task 5 Postman collection. Do **not** reset Docker volumes or remove the existing PostgreSQL/RabbitMQ data.

## Tests

Run targeted tests using the existing Maven Docker image, without installing Maven on Windows:

```powershell
cd D:\dev\microservices-lab

docker run --rm -v "${PWD}:/work" -v task6-maven-cache:/root/.m2 `
  -w /work/client-service maven:3.9-eclipse-temurin-17 `
  mvn -B '-Dtest=ClientEventPublisherTest,ClientEventControllerTest' test

docker run --rm -v "${PWD}:/work" -v task6-maven-cache:/root/.m2 `
  -w /work/storage-service maven:3.9-eclipse-temurin-17 `
  mvn -B '-Dtest=com.example.storage.kafka.ClientEventListenerTest' test

docker run --rm -v "${PWD}:/work" -v task6-maven-cache:/root/.m2 `
  -w /work/audit-service maven:3.9-eclipse-temurin-17 `
  mvn -B test
```

The three suites use mocks or direct listener calls; no external Kafka broker is required for the unit tests. Docker Compose builds include Java compilation but skip tests inside the image build. A provided Postman collection is at `postman/Task6-Kafka-Client-Events.postman_collection.json`.

## Consumer-group behavior and scope

One topic with one partition is enough here because **each different group receives its own copy of the event**. Two consumers with the **same** group ID would instead divide partitions between instances, potentially leaving one without the event. Consumer group IDs are explicit: `storage-service-client-events` and `audit-service-client-events`. Both groups use `auto-offset-reset: earliest` when there is no previous committed offset.

Events are JSON text with the same documented field names. Each service parses JSON locally; neither consumer imports Java classes from Client Service or the other consumer. The audit consumer only records receipt in application logs; no audit database is required by this assignment.

A single-node Kafka broker is for classroom demonstration, not high availability. The example Kafka data is ephemeral if the broker container is deleted/recreated; existing PostgreSQL and RabbitMQ volumes are retained. Kafka consumer delivery is normally at least once; if strict deduplication or atomic DB writes are later required, use persisted event IDs and transaction/outbox patterns. The current assignment asks for logging, not exactly-once persistence.
