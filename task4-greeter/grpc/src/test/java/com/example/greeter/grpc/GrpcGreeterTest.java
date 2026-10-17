package com.example.greeter.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcGreeterTest {
    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws IOException {
        String serverName = UUID.randomUUID().toString();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new GrpcGreeterService())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void saysHelloToAda() {
        assertEquals("Hello, Ada", GrpcGreeterClient.sayHello(channel, "Ada"));
    }

    @Test
    void emptyNameReturnsInvalidArgument() {
        StatusRuntimeException failure = assertThrows(StatusRuntimeException.class,
                () -> GrpcGreeterClient.sayHello(channel, ""));
        assertEquals(Status.Code.INVALID_ARGUMENT, failure.getStatus().getCode());
        assertEquals("name must not be empty", failure.getStatus().getDescription());
        assertEquals(Status.Code.INVALID_ARGUMENT,
                assertThrows(StatusRuntimeException.class,
                        () -> GrpcGreeterClient.sayHello(channel, "   ")).getStatus().getCode());
    }
}
