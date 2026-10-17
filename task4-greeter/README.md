# Task 4 — Greeter with gRPC and JSON-RPC

Two independent Java 17 Maven modules expose the same operation:

`sayHello("Ada")` returns `Hello, Ada`.

- `grpc/`: Protocol Buffers, a gRPC server on TCP port **50051**, and a blocking client with a **2-second deadline**.
- `rpc/`: JSON-RPC 2.0 over HTTP, a `POST /rpc` server on **localhost:8083**, and an HTTP client with **2-second connect and request timeouts**.

Both are independent of the Task 1–3 Spring Boot services and database.

## Run in four steps

Requires **JDK 17+** and **Maven 3.9+**. Use separate PowerShell windows for the servers.

1. From the repository root, enter the project and run all four tests (this also generates the Java gRPC classes):

   ```powershell
   cd task4-greeter
   mvn clean test
   ```

2. **Terminal A** — start the gRPC server:

   ```powershell
   mvn -pl grpc exec:java -Dexec.mainClass=com.example.greeter.grpc.GrpcGreeterServer
   ```

3. **Terminal B** — start the JSON-RPC server:

   ```powershell
   mvn -pl rpc exec:java -Dexec.mainClass=com.example.greeter.rpc.JsonRpcServer
   ```

4. **Terminal C** — run each client. Both print **`Hello, Ada`**:

   ```powershell
   mvn -pl grpc exec:java -Dexec.mainClass=com.example.greeter.grpc.GrpcGreeterClient -Dexec.args=Ada
   mvn -pl rpc exec:java -Dexec.mainClass=com.example.greeter.rpc.JsonRpcClient -Dexec.args=Ada
   ```

Stop either server with `Ctrl+C` in its terminal. On Windows PowerShell, run each command on its own line. From the `task4-greeter` directory, `mvn test` reruns every test.

If Maven is not installed locally but Docker Desktop is running, the tests can be run from `task4-greeter` with:

```powershell
docker run --rm -v "${PWD}:/work" -w /work maven:3.9-eclipse-temurin-17 mvn test
```

## Empty-name errors

Both clients accept an optional name argument. To check validation, replace `-Dexec.args=Ada` with `-Dexec.args=" "` (one space):

- **gRPC:** `INVALID_ARGUMENT: name must not be empty` (gRPC status code 3).
- **JSON-RPC:** a JSON-RPC 2.0 `error` with code **-32602** (`Invalid params`) and message `name must not be empty`; the Java client reports that error. JSON-RPC errors use HTTP status 200 because the RPC error is represented in the JSON response.

Example JSON-RPC request to `http://localhost:8083/rpc`:

```json
{"jsonrpc":"2.0","method":"sayHello","params":{"name":"Ada"},"id":1}
```

Successful response:

```json
{"jsonrpc":"2.0","result":"Hello, Ada","id":1}
```

Empty-name response:

```json
{"jsonrpc":"2.0","error":{"code":-32602,"message":"name must not be empty"},"id":1}
```

The gRPC schema is `grpc/src/main/proto/greeter.proto`. Maven's `protobuf-maven-plugin` invokes `protoc` and the Java gRPC generator during `mvn compile`, writing generated sources under `grpc/target/generated-sources/`. No generated Java files need to be committed.

## Comparison

| Aspect | gRPC | Plain JSON-RPC over HTTP |
|---|---|---|
| Message format | Binary Protocol Buffers | Human-readable JSON |
| Transport | HTTP/2 with gRPC framing | HTTP `POST /rpc` |
| Speed | Compact payloads and efficient multiplexing can help with throughput/latency | Parsing textual JSON and HTTP request overhead can be greater; actual speed depends on workload |
| Ease of use | More setup: `.proto` schema and generated types | Easy to inspect with curl/Postman and standard HTTP libraries |
| Tooling | `protoc`, gRPC stubs, grpcurl (optional) | HTTP clients, Postman, JSON tools |
| Errors | gRPC status `INVALID_ARGUMENT` | JSON-RPC structured `error` object (`-32602`) |
| Timeouts | 2-second client deadline | 2-second HTTP connect/request timeout |

These are architectural differences, not benchmark measurements. Both implementations are local development demos and use plaintext connections.
