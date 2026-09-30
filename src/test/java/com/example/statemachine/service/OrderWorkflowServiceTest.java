package com.example.statemachine.service;

import com.example.statemachine.model.OrderEvents;
import com.example.statemachine.model.OrderStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.state.State;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderWorkflowServiceTest {

    private final StateMachineFactory<OrderStates, OrderEvents> stateMachineFactory = mock(StateMachineFactory.class);
    private final StateMachine<OrderStates, OrderEvents> stateMachine = mock(StateMachine.class);
    private final State<OrderStates, OrderEvents> state = mock(State.class);
    private final AtomicReference<OrderStates> currentState = new AtomicReference<>(OrderStates.SUBMITTED);
    private OrderWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        when(stateMachineFactory.getStateMachine("ORD-STALE")).thenReturn(stateMachine);
        when(stateMachine.startReactively()).thenReturn(Mono.empty());
        when(stateMachine.getState()).thenReturn(state);
        when(state.getId()).thenAnswer(invocation -> currentState.get());
        workflowService = new OrderWorkflowService(stateMachineFactory);
        workflowService.createOrder("ORD-STALE", "Asha Rao", "asha@example.com");
    }

    @Test
    void includesInactiveNonTerminalOrdersAndReportsActivityAge() {
        List<OrderWorkflowService.StaleOrder> staleOrders =
                workflowService.getStaleOrders(Duration.ZERO);

        assertEquals(1, staleOrders.size());
        assertEquals("ORD-STALE", staleOrders.get(0).details().orderId());
        assertEquals(OrderStates.SUBMITTED, staleOrders.get(0).currentState());
        assertTrue(staleOrders.get(0).inactiveForSeconds() >= 0);
    }

    @Test
    void excludesRecentAndTerminalOrders() {
        assertTrue(workflowService.getStaleOrders(Duration.ofHours(1)).isEmpty());

        currentState.set(OrderStates.DELIVERED);
        assertTrue(workflowService.getStaleOrders(Duration.ZERO).isEmpty());
    }
}
