package com.example.greeter.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public final class JsonRpcServer implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpServer server;

    public JsonRpcServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/rpc", this::handle);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        JsonNode id = null;
        ObjectNode response = JSON.createObjectNode();
        response.put("jsonrpc", "2.0");

        try {
            JsonNode request = JSON.readTree(exchange.getRequestBody());
            if (request != null && request.isObject()) {
                id = request.get("id");
            }
            if (request == null || !request.isObject()
                    || !"2.0".equals(request.path("jsonrpc").asText())
                    || id == null || !(id.isTextual() || id.isIntegralNumber())) {
                throw new RpcFault(-32600, "Invalid Request");
            }
            if (!"sayHello".equals(request.path("method").asText())) {
                throw new RpcFault(-32601, "Method not found");
            }
            JsonNode params = request.path("params");
            JsonNode nameNode = params.path("name");
            if (!params.isObject() || !nameNode.isTextual()
                    || nameNode.asText().isBlank()) {
                throw new RpcFault(-32602, "name must not be empty");
            }
            response.put("result", "Hello, " + nameNode.asText().trim());
        } catch (JsonProcessingException malformedJson) {
            response.set("error", error(-32700, "Parse error"));
            id = null;
        } catch (RpcFault fault) {
            response.set("error", error(fault.code, fault.getMessage()));
        }

        if (id == null) {
            response.putNull("id");
        } else {
            response.set("id", id);
        }
        byte[] body = JSON.writeValueAsBytes(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static ObjectNode error(int code, String message) {
        ObjectNode error = JSON.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        return error;
    }

    private static final class RpcFault extends RuntimeException {
        private final int code;

        private RpcFault(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        JsonRpcServer greeter = new JsonRpcServer(8083);
        greeter.start();
        System.out.println("JSON-RPC Greeter listening on http://localhost:8083/rpc");
        Runtime.getRuntime().addShutdownHook(new Thread(greeter::close));
        new java.util.concurrent.CountDownLatch(1).await();
    }
}
