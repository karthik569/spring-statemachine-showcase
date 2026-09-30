package com.example.statemachine.model;

public enum OrderEvents {
    PAY,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    RETRY_PAYMENT,
    START_PREPARING,
    DISPATCH,
    DELIVER,
    CANCEL
}
