package com.example.order.payment;

public class BadRequestPaymentException extends RuntimeException {
    public BadRequestPaymentException(String message) {
        super(message);
    }
}
