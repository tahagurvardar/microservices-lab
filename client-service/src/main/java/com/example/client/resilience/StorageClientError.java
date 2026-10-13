package com.example.client.resilience;

import feign.FeignException;

import java.util.function.Predicate;

public class StorageClientError implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable failure) {
        return failure instanceof FeignException feign
                && feign.status() >= 400 && feign.status() < 500;
    }
}
