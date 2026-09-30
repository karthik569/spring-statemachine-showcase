package com.example.statemachine.model;

public enum OrderStates {
    SUBMITTED,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    PAID,
    PREPARING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
