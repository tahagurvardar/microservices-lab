package com.example.client.resilience;

import feign.FeignException;

import java.util.function.Predicate;

public class TransientStorageFailure implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable failure) {
        return failure instanceof FeignException feign
                && (feign.status() < 0 || feign.status() >= 500);
    }
}
