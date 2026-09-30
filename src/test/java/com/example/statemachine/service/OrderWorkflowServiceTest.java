package com.example.statemachine.service;

import com.example.statemachine.model.OrderEvents;
import com.example.statemachine.model.OrderStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.state.State;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderWorkflowServiceTest {

    private final StateMachineFactory<OrderStates, OrderEvents> stateMachineFactory = mock(StateMachineFactory.class);
    private final StateMachine<OrderStates, OrderEvents> stateMachine = mock(StateMachine.class);
    private final State<OrderStates, OrderEvents> state = mock(State.class);
    private final ExtendedState extendedState = mock(ExtendedState.class);
    private final Map<Object, Object> variables = new ConcurrentHashMap<>();
    private final AtomicReference<OrderStates> currentState = new AtomicReference<>(OrderStates.SUBMITTED);
    private OrderWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        when(stateMachineFactory.getStateMachine("ORD-STALE")).thenReturn(stateMachine);
        when(stateMachine.startReactively()).thenReturn(Mono.empty());
        when(stateMachine.getState()).thenReturn(state);
        when(stateMachine.getExtendedState()).thenReturn(extendedState);
        when(extendedState.getVariables()).thenReturn(variables);
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

    @Test
    void filtersHistoryAndReturnsTheMostRecentMatchingEntries() {
        @SuppressWarnings("unchecked")
        StateMachineEventResult<OrderStates, OrderEvents> acceptedResult = mock(StateMachineEventResult.class);
        when(acceptedResult.getResultType()).thenReturn(StateMachineEventResult.ResultType.ACCEPTED);
        when(stateMachine.sendEvent(any(Mono.class))).thenReturn(Flux.just(acceptedResult));
        workflowService.sendEvent("ORD-STALE", OrderEvents.PAY);
        workflowService.sendEvent("ORD-STALE", OrderEvents.CANCEL);
        workflowService.sendEvent("ORD-STALE", OrderEvents.PAY);

        var historyPage = workflowService.getHistory("ORD-STALE", OrderEvents.PAY, false, 1);

        assertEquals(2, historyPage.totalEntries());
        assertEquals(1, historyPage.entries().size());
        assertEquals(OrderEvents.PAY, historyPage.entries().get(0).event());
        assertFalse(historyPage.entries().get(0).accepted());
    }
}
