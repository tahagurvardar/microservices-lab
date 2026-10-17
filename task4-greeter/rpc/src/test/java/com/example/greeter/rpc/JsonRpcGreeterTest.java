package com.example.greeter.rpc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonRpcGreeterTest {
    private JsonRpcServer server;
    private URI endpoint;

    @BeforeEach
    void setUp() throws IOException {
        server = new JsonRpcServer(0);
        server.start();
        endpoint = URI.create("http://localhost:" + server.port() + "/rpc");
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void saysHelloToAda() throws Exception {
        assertEquals("Hello, Ada", JsonRpcClient.sayHello(endpoint, "Ada"));
    }

    @Test
    void emptyNameReturnsInvalidParams() {
        JsonRpcClient.RpcException failure = assertThrows(JsonRpcClient.RpcException.class,
                () -> JsonRpcClient.sayHello(endpoint, ""));
        assertEquals(-32602, failure.code());
        assertEquals("name must not be empty", failure.getMessage());
        assertEquals(-32602, assertThrows(JsonRpcClient.RpcException.class,
                () -> JsonRpcClient.sayHello(endpoint, "   ")).code());
    }
}
