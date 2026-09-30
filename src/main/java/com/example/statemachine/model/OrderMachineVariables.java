package com.example.statemachine.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class OrderMachineVariables {

    public static final String PAYMENT_RETRY_COUNT = "paymentRetryCount";
    public static final String DELIVERED_AT = "deliveredAt";
    public static final int MAX_PAYMENT_RETRIES = 3;
    public static final int RETURN_WINDOW_DAYS = 30;

    private OrderMachineVariables() {
    }

    public static boolean isReturnWindowOpen(Instant deliveredAt, Instant now) {
        return deliveredAt != null && now.isBefore(deliveredAt.plus(RETURN_WINDOW_DAYS, ChronoUnit.DAYS));
    }
}
