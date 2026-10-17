package com.example.greeter.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public final class GrpcGreeterServer {
    private GrpcGreeterServer() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(50051)
                .addService(new GrpcGreeterService())
                .build()
                .start();
        System.out.println("gRPC Greeter listening on port 50051");

        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
        server.awaitTermination();
    }
}
