package com.example.statemachine.service;

import com.example.statemachine.model.OrderEvents;
import com.example.statemachine.model.OrderStates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(OrderWorkflowService.class);

    private final StateMachineFactory<OrderStates, OrderEvents> stateMachineFactory;
    private final Map<String, StateMachine<OrderStates, OrderEvents>> machines = new ConcurrentHashMap<>();

    public OrderWorkflowService(StateMachineFactory<OrderStates, OrderEvents> stateMachineFactory) {
        this.stateMachineFactory = stateMachineFactory;
    }

    public StateMachine<OrderStates, OrderEvents> createOrder(String orderId) {
        StateMachine<OrderStates, OrderEvents> sm = stateMachineFactory.getStateMachine(orderId);
        sm.startReactively().subscribe();
        machines.put(orderId, sm);
        log.info("[WORKFLOW] Created state machine for orderId={} (Initial: {})", orderId, sm.getState().getId());
        return sm;
    }

    public boolean sendEvent(String orderId, OrderEvents event) {
        StateMachine<OrderStates, OrderEvents> sm = machines.get(orderId);
        if (sm == null) {
            sm = createOrder(orderId);
        }

        log.info("[WORKFLOW] Sending event {} to orderId {} (Current: {})", event, orderId, sm.getState().getId());
        var message = MessageBuilder.withPayload(event).setHeader("orderId", orderId).build();
        return Boolean.TRUE.equals(sm.sendEvent(Mono.just(message)).blockLast().getResultType() != null);
    }

    public OrderStates getOrderState(String orderId) {
        StateMachine<OrderStates, OrderEvents> sm = machines.get(orderId);
        return sm != null ? sm.getState().getId() : OrderStates.SUBMITTED;
    }
}
