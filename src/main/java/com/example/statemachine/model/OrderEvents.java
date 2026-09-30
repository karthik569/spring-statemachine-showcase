package com.example.statemachine.model;

public enum OrderEvents {
    PAY,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    RETRY_PAYMENT,
    START_PREPARING,
    DISPATCH,
    DISPATCH_FAILED,
    RETRY_DISPATCH,
    DELIVER,
    REQUEST_RETURN,
    APPROVE_RETURN,
    REJECT_RETURN,
    RECEIVE_RETURN,
    CANCEL
}
