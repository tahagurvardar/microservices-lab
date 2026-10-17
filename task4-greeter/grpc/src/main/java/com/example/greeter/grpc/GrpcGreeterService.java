package com.example.greeter.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public final class GrpcGreeterService extends GreeterGrpc.GreeterImplBase {
    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> observer) {
        String name = request.getName().trim();
        if (name.isEmpty()) {
            observer.onError(Status.INVALID_ARGUMENT
                    .withDescription("name must not be empty")
                    .asRuntimeException());
            return;
        }

        observer.onNext(HelloReply.newBuilder().setMessage("Hello, " + name).build());
        observer.onCompleted();
    }
}
