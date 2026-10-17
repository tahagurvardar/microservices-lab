package com.example.greeter.grpc;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.TimeUnit;

public final class GrpcGreeterClient {
    private GrpcGreeterClient() {
    }

    public static String sayHello(Channel channel, String name) {
        HelloRequest request = HelloRequest.newBuilder().setName(name).build();
        HelloReply reply = GreeterGrpc.newBlockingStub(channel)
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .sayHello(request);
        return reply.getMessage();
    }

    public static void main(String[] args) throws InterruptedException {
        String name = args.length == 0 ? "Ada" : args[0];
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051)
                .usePlaintext()
                .build();
        try {
            System.out.println(sayHello(channel, name));
        } catch (StatusRuntimeException failure) {
            System.err.println("gRPC " + failure.getStatus().getCode()
                    + ": " + failure.getStatus().getDescription());
            System.exit(1);
        } finally {
            channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        }
    }
}
