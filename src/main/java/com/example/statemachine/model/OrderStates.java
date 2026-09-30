package com.example.statemachine.model;

public enum OrderStates {
    SUBMITTED,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    PAID,
    PREPARING,
    SHIPPED,
    DELIVERED,
    RETURN_REQUESTED,
    RETURN_APPROVED,
    RETURNED,
    CANCELLED
}
