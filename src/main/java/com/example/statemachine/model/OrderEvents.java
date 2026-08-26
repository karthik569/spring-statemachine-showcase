package com.example.statemachine.model;

public enum OrderEvents {
    PAY,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    START_PREPARING,
    DISPATCH,
    DELIVER,
    CANCEL
}
