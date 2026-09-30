package com.example.statemachine.model;

public final class OrderMachineVariables {

    public static final String PAYMENT_RETRY_COUNT = "paymentRetryCount";
    public static final int MAX_PAYMENT_RETRIES = 3;

    private OrderMachineVariables() {
    }
}
