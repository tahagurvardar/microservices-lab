# Microservices Lab

A university assignment demonstrating HTTP communication between two Spring Boot services and persistent message storage in PostgreSQL.

## Architecture

```text
User
  |
  v
client-service :8081
  |
  | OpenFeign / HTTP
  v
storage-service :8082
  |
  | JPA / Hibernate
  v
PostgreSQL :5432
```

| Component | Responsibility | Host port |
| --- | --- | --- |
| client-service | Public API, input validation, author/text processing, calls to storage | 8081 |
| storage-service | Save and retrieve message entities through a JPA repository | 8082 |
| PostgreSQL 16 | Store the `messages` table in database `storagedb` | 5432 |

The services use Java 17 and Spring Boot 3.3.5. The client uses Spring Cloud OpenFeign with the Spring Cloud 2023.0.3 dependency BOM. Spring Data JPA provides repository operations, and Hibernate maps the `Message` entity to the database table.

Docker Compose builds and runs both applications and PostgreSQL. The database's Compose service name is `postgres`; its container name is `postgres-db`. The database user is `storage_user`. Inside Docker, the client calls `http://storage-service:8082`, and storage connects to `postgres:5432`. Compose supplies these addresses through environment variables.

## Start and stop

Prerequisite: Docker Desktop running with Linux containers; ports 8081, 8082 and 5432 available. Docker performs the Java builds, so host Maven is optional.

Create a local `.env` file in the project root containing `DB_PASSWORD=<your database password>`. This file is ignored by Git. When using an existing PostgreSQL volume, use its existing password. For a host Maven build, set the same `DB_PASSWORD` environment variable before running Maven.

From the directory containing `docker-compose.yml`:

```powershell
docker compose -p microservices-lab config
docker compose -p microservices-lab up -d --build
docker compose -p microservices-lab ps
```

Wait for `postgres-db` to be healthy and both services to finish starting. A running container alone does not prove the application is ready; the ping endpoints below verify readiness.

The explicit project name keeps the same Docker resources when running from the submission folder or an extracted ZIP. In the original folder `C:\dev\microservices-lab`, the shorter `docker compose up -d --build` uses the same default project name. Use one copy of this system at a time because container names and published ports are fixed.

Stop and remove the containers while retaining stored messages:

```powershell
docker compose -p microservices-lab down
```

Start them again:

```powershell
docker compose -p microservices-lab up -d
```

The named volume `postgres-data` is mounted at `/var/lib/postgresql/data`. With the project name above, its Docker name is `microservices-lab_postgres-data`. Ordinary `down` retains it; the subsequent `up` reuses it. Do not use the volume-removal option or delete this volume when testing persistence. The ZIP contains source and evidence; live database data stays in the local Docker volume.

## Endpoints

| Method | URL | Result |
| --- | --- | --- |
| GET | `http://localhost:8082/api/storage/ping` | Storage service status |
| GET | `http://localhost:8081/api/client/ping-storage` | Client response containing the storage response obtained through Feign |
| GET | `http://localhost:8081/api/client/greet/Docker` | Greeting produced by storage through Feign |
| POST | `http://localhost:8081/api/messages` | Process and save a message; HTTP 201 |
| GET | `http://localhost:8081/api/messages` | JSON array of stored messages |
| POST / GET | `http://localhost:8082/api/storage/messages` | Internal storage API for processed messages / retrieval |
| GET | `http://localhost:8082/api/storage/greet/{name}` | Storage greeting |

The storage POST accepts `author`, `text`, `wordCount` and `processedAt`. Users should send unprocessed input to the client API.

## Example request

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8081/api/messages `
  -ContentType "application/json" `
  -Body '{"author":" aLiCe ","text":" hello microservices world "}'
```

The response contains a generated `id`, `author = Alice`, `text = hello microservices world`, `wordCount = 3`, `processedAt` and `savedAt`. IDs and timestamps vary between calls.

The client rejects null or blank author/text values. It trims the author and makes its first character uppercase and remaining characters lowercase. It trims text, replaces consecutive whitespace with a single space, and counts the resulting words. `processedAt` is created in the client. Storage assigns `savedAt` just before the entity is inserted. Both timestamps are Java `LocalDateTime` values without a time-zone offset.

Retrieve messages:

```powershell
Invoke-RestMethod http://localhost:8081/api/messages | ConvertTo-Json
```

### Validation

```powershell
try {
  Invoke-RestMethod -Method Post `
    -Uri http://localhost:8081/api/messages `
    -ContentType "application/json" `
    -Body '{"author":"Bob","text":" "}'
}
catch {
  Write-Host "HTTP STATUS:" $_.Exception.Response.StatusCode.value__
}
```

Expected result: `HTTP STATUS: 400`. Validation takes place before the Feign storage call, so the invalid message is not inserted.

## Database inspection

```powershell
docker exec postgres-db psql -U storage_user -d storagedb -c "\dt"
docker exec postgres-db psql -U storage_user -d storagedb -c "SELECT * FROM messages;"
```

Hibernate's `ddl-auto: update` creates or updates the mapped schema without intentionally dropping existing messages.

## Build checks

With Java 17 and Maven installed, run from each service directory:

```powershell
mvn -B clean package
```

The included Maven wrappers are also available as `mvnw.cmd` on Windows and `./mvnw` on Linux. Storage's existing context test needs PostgreSQL running and reachable through its configured datasource. Both services passed `mvn -B clean package`, including their existing context tests, during delivery preparation on 30 September 2026. Dockerfiles use multi-stage builds and skip tests inside the image build.
