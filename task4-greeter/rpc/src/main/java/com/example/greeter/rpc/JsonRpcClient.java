package com.example.greeter.rpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class JsonRpcClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private JsonRpcClient() {
    }

    public static String sayHello(URI endpoint, String name)
            throws IOException, InterruptedException {
        ObjectNode params = JSON.createObjectNode();
        params.put("name", name);
        ObjectNode request = JSON.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("method", "sayHello");
        request.set("params", params);
        request.put("id", 1);

        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(request)))
                .build();
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        HttpResponse<String> response = client.send(httpRequest,
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        JsonNode result = JSON.readTree(response.body());
        if (!"2.0".equals(result.path("jsonrpc").asText())
                || result.path("id").asInt(-1) != 1) {
            throw new IOException("Invalid JSON-RPC response");
        }
        if (result.has("error")) {
            JsonNode error = result.get("error");
            throw new RpcException(error.path("code").asInt(),
                    error.path("message").asText());
        }
        if (!result.path("result").isTextual()) {
            throw new IOException("JSON-RPC result is missing");
        }
        return result.get("result").asText();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String name = args.length == 0 ? "Ada" : args[0];
        try {
            System.out.println(sayHello(URI.create("http://localhost:8083/rpc"), name));
        } catch (RpcException failure) {
            System.err.println("JSON-RPC error " + failure.code() + ": " + failure.getMessage());
            System.exit(1);
        }
    }

    public static final class RpcException extends RuntimeException {
        private final int code;

        public RpcException(int code, String message) {
            super(message);
            this.code = code;
        }

        public int code() {
            return code;
        }
    }
}
