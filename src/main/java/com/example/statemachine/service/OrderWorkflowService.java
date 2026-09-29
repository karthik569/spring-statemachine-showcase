package com.example.statemachine.service;

import com.example.statemachine.model.OrderEvents;
import com.example.statemachine.model.OrderStates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.EnumMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class OrderWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(OrderWorkflowService.class);

    private final StateMachineFactory<OrderStates, OrderEvents> stateMachineFactory;
    private final Map<String, StateMachine<OrderStates, OrderEvents>> machines = new ConcurrentHashMap<>();
    private final Map<String, List<TransitionRecord>> history = new ConcurrentHashMap<>();

    public OrderWorkflowService(StateMachineFactory<OrderStates, OrderEvents> stateMachineFactory) {
        this.stateMachineFactory = stateMachineFactory;
    }

    public StateMachine<OrderStates, OrderEvents> createOrder(String orderId) {
        StateMachine<OrderStates, OrderEvents> sm = stateMachineFactory.getStateMachine(orderId);
        sm.startReactively().subscribe();
        machines.put(orderId, sm);
        history.put(orderId, new CopyOnWriteArrayList<>());
        log.info("[WORKFLOW] Created state machine for orderId={} (Initial: {})", orderId, sm.getState().getId());
        return sm;
    }

    public boolean sendEvent(String orderId, OrderEvents event) {
        StateMachine<OrderStates, OrderEvents> sm = machines.get(orderId);
        if (sm == null) {
            return false;
        }

        synchronized (sm) {
            OrderStates previousState = sm.getState().getId();
            log.info("[WORKFLOW] Sending event {} to orderId {} (Current: {})", event, orderId, previousState);
            var message = MessageBuilder.withPayload(event).setHeader("orderId", orderId).build();
            StateMachineEventResult<OrderStates, OrderEvents> result =
                    sm.sendEvent(Mono.just(message)).blockLast();
            boolean accepted = result != null
                    && result.getResultType() == StateMachineEventResult.ResultType.ACCEPTED;
            OrderStates currentState = sm.getState().getId();
            history.get(orderId).add(new TransitionRecord(Instant.now(), event, previousState, currentState, accepted));
            return accepted;
        }
    }

    public OrderStates getOrderState(String orderId) {
        StateMachine<OrderStates, OrderEvents> sm = machines.get(orderId);
        return sm != null ? sm.getState().getId() : null;
    }

    public List<OrderEvents> getAvailableEvents(String orderId) {
        StateMachine<OrderStates, OrderEvents> sm = machines.get(orderId);
        if (sm == null) {
            return List.of();
        }
        OrderStates currentState = sm.getState().getId();
        return sm.getTransitions().stream()
                .filter(transition -> transition.getSource().getId() == currentState)
                .map(transition -> transition.getTrigger() == null ? null : transition.getTrigger().getEvent())
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    public boolean orderExists(String orderId) {
        return machines.containsKey(orderId);
    }

    public Map<String, OrderStates> getOrders() {
        Map<String, OrderStates> orders = new java.util.TreeMap<>();
        machines.forEach((id, machine) -> orders.put(id, machine.getState().getId()));
        return orders;
    }

    public Map<OrderStates, Long> getOrderCountsByState() {
        Map<OrderStates, Long> counts = new EnumMap<>(OrderStates.class);
        for (OrderStates state : OrderStates.values()) {
            counts.put(state, 0L);
        }
        getOrders().values().forEach(state -> counts.compute(state, (key, count) -> count + 1));
        return counts;
    }

    public List<TransitionRecord> getHistory(String orderId) {
        List<TransitionRecord> records = history.get(orderId);
        return records == null ? List.of() : List.copyOf(records);
    }

    public record TransitionRecord(Instant timestamp, OrderEvents event, OrderStates previousState,
                                   OrderStates currentState, boolean accepted) {
    }
}
