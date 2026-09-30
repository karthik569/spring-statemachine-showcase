package com.example.statemachine.model;

public enum OrderStates {
    SUBMITTED,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    PAYMENT_DECISION,
    PAYMENT_REVIEW,
    PAID,
    PREPARING,
    DISPATCH_FAILED,
    SHIPPED,
    DELIVERED,
    RETURN_REQUESTED,
    RETURN_APPROVED,
    RETURNED,
    CANCELLED
}
