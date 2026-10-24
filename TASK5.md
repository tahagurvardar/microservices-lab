# Task 5 — Event-driven email notifications

## Architecture

```text
POST /api/messages
      |
      v
Client Service (8081) --Feign--> Storage Service (8082)
                                     |         |
                                     |         +--> messages (PostgreSQL)
                                     +------------> notification_outbox (same DB transaction)
                                                      |
                                              scheduled publisher
                                                      |
                                                RabbitMQ (5672)
                                             email.notifications.queue
                                                      |
                                                   Email Service
                                                      |
                                                  SMTP (1025)
                                                      |
                                           Mailpit inbox (8025)
```

The **designated endpoint** is the existing `POST /api/storage/messages`, reached through the existing public `POST /api/messages`. No change to the Client Service request or response is required. A successful save returns HTTP 201 and the existing message JSON. Storage writes a notification event into `notification_outbox` **in the same PostgreSQL transaction** as the message. A scheduled publisher sends pending events to RabbitMQ and waits for a publisher confirmation before marking the event as sent. If RabbitMQ is unavailable, stored messages and pending outbox rows remain intact; the publisher retries automatically.

The notification event is JSON:

```json
{
  "eventId": "unique-uuid",
  "messageId": 42,
  "recipient": "demo@example.test",
  "subject": "New message stored (#42)",
  "body": "Author: Ada\nMessage: Hello\nWord count: 1",
  "occurredAt": "2026-10-24T12:00:00Z"
}
```

The configured recipient is a single notification mailbox (`NOTIFICATION_RECIPIENT`); existing client requests do not contain an email address. For this demo, all created messages notify that address. Changing the notification recipient does not change existing message validation, processing, storage, or response fields.

## Demonstration in four steps

1. In the existing repository `.env` (ignored by Git), **retain** your existing `DB_PASSWORD` and add these lines. Choose a unique password:

   ```dotenv
   RABBITMQ_USER=labuser
   RABBITMQ_PASSWORD=replace-with-a-long-random-password
   NOTIFICATION_RECIPIENT=demo@example.test
   ```

2. Open Docker Desktop. From `C:\dev\microservices-lab`, start the stack. If the existing local Feign timeout override exists at `C:\dev\microservices-lab.local.yml`, retain it:

   ```powershell
   cd C:\dev\microservices-lab
   docker compose -f .\docker-compose.yml -f ..\microservices-lab.local.yml up -d --build
   docker compose ps
   ```

   The first build can take a while. The containers to see are `client-service`, `storage-service`, `postgres-db` (healthy), `rabbitmq`, `email-service`, and `mailpit`. If your local override does not exist, use `docker compose up -d --build` instead.

3. Create a new message through the **Client Service**, exactly as in Tasks 1–3:

   ```powershell
   Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/messages `
     -ContentType 'application/json' `
     -Body '{"author":"Ada","text":"Task 5 RabbitMQ email demonstration"}'
   ```

   Expect HTTP 201 with a stored message ID. The message is stored first; its email is sent asynchronously. The event may take several seconds to reach RabbitMQ and Mailpit.

4. Open **http://localhost:8025** to see the received message (`New message stored (#ID)`). Inspect the RabbitMQ Management UI at **http://localhost:15672** using credentials from `.env`. The normal queue is `email.notifications.queue`; the failed-message queue is `email.notifications.dead`.

You can also import `postman/Task5-Email-Notifications.postman_collection.json` and run its three requests. Its create-message assertion checks HTTP 201; use the Mailpit UI for the asynchronous email assertion.

## Failure, recovery and verification

* To demonstrate a durable queue, stop only the consumer, create a new message, inspect the queued message in RabbitMQ, then restart the consumer:

  ```powershell
  docker compose stop email-service
  # Send a new POST /api/messages request.
  docker compose start email-service
  ```

* To demonstrate outbox recovery, stop only RabbitMQ, send a new `POST /api/messages` request (which should still return 201), then restart the broker:

  ```powershell
  docker compose stop rabbitmq
  # Send a new POST /api/messages request; check its saved ID.
  docker compose start rabbitmq
  ```

  The outbox publisher will retry pending entries after RabbitMQ is available again. Avoid running the Task 3 storage-failure demonstration at the same time as this test.

* Inspect recent events and outbox rows:

  ```powershell
  docker compose logs --tail 100 storage-service email-service
  docker exec postgres-db psql -U storage_user -d storagedb `
    -c 'SELECT id, event_id, sent_at FROM notification_outbox ORDER BY id DESC LIMIT 10;'
  ```

* Error handling: Email Service uses automatic acknowledgment **only after successful SMTP delivery**. Handler failures trigger up to **three attempts** with bounded backoff. Exhausted messages are rejected without requeue and RabbitMQ routes them to the durable `email.notifications.dead` dead-letter queue. Invalid payloads fail validation and use the same error path. Both regular and dead-letter queues are durable. Publisher confirms and mandatory routing returns protect the Storage Service outbox against unconfirmed publication.

* **Delivery semantics:** This is at-least-once processing. A process crash between an SMTP send and queue acknowledgment (or between a RabbitMQ confirmation and outbox DB update) can produce a duplicate. Production systems should add consumer-side idempotency and operational dead-letter reprocessing. Mailpit captures test email locally; it does **not** deliver email to the public internet.

## Real SMTP delivery

The Email Service sends through `JavaMailSender`. To send to real mailboxes, configure an SMTP provider via `.env` using `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_SMTP_AUTH=true`, and `MAIL_SMTP_STARTTLS=true` where supported. Set `NOTIFICATION_RECIPIENT` to an address you control. The `email-service` container reads these settings; keep all secrets in ignored `.env`, not in source control. Mailpit remains available for local testing unless you choose to disable it in Compose.

## Tests and build

Both services use Java 17 and Spring Boot 3.3.5. The Email Service tests use a mocked SMTP sender and do not send real email. The Storage Service notification tests use mocked repositories and verify the saved message plus outbox event.

With host Maven installed:

```powershell
cd email-service
mvn test
cd ..\storage-service
mvn -Dtest=MessageServiceNotificationTest test
```

Without host Maven, use Docker from the repository root:

```powershell
docker run --rm -v "${PWD}:/work" -v task5-maven-cache:/root/.m2 `
  -w /work/email-service maven:3.9-eclipse-temurin-17 mvn -B test

docker run --rm -v "${PWD}:/work" -v task5-maven-cache:/root/.m2 `
  -w /work/storage-service maven:3.9-eclipse-temurin-17 `
  mvn -B -Dtest=MessageServiceNotificationTest test
```

The existing Storage Service context test requires PostgreSQL to be accessible; run the complete test suite when its database is configured. Docker Compose `--build` also compiles both applications, but Dockerfiles skip unit tests during image creation.

## Differences from previous tasks

* Client Service: **no changes**.
* Storage Service: existing save/list endpoints and response format remain unchanged; a new transactional outbox and asynchronous RabbitMQ publisher are added.
* Email Service: independent Java process with its own Docker build, RabbitMQ consumer, retry policy, and SMTP configuration.
* PostgreSQL: existing `postgres-data` named volume remains unchanged; Hibernate creates the additional `notification_outbox` table. Do not use `docker compose down -v`.
* RabbitMQ: messages flow through a durable direct exchange and queues; management UI is bound to loopback only.
