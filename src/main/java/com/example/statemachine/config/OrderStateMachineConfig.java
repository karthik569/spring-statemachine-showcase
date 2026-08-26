package com.example.statemachine.config;

import com.example.statemachine.model.OrderEvents;
import com.example.statemachine.model.OrderStates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

import java.util.EnumSet;

@Configuration
@EnableStateMachineFactory
public class OrderStateMachineConfig extends EnumStateMachineConfigurerAdapter<OrderStates, OrderEvents> {

    private static final Logger log = LoggerFactory.getLogger(OrderStateMachineConfig.class);

    @Override
    public void configure(StateMachineConfigurationConfigurer<OrderStates, OrderEvents> config) throws Exception {
        config
                .withConfiguration()
                .autoStartup(true)
                .listener(new StateMachineListenerAdapter<>() {
                    @Override
                    public void stateChanged(State<OrderStates, OrderEvents> from, State<OrderStates, OrderEvents> to) {
                        log.info("[STATE-MACHINE] Transition: {} -> {}",
                                from != null ? from.getId() : "START",
                                to != null ? to.getId() : "END");
                    }
                });
    }

    @Override
    public void configure(StateMachineStateConfigurer<OrderStates, OrderEvents> states) throws Exception {
        states
                .withStates()
                .initial(OrderStates.SUBMITTED)
                .states(EnumSet.allOf(OrderStates.class))
                .end(OrderStates.DELIVERED)
                .end(OrderStates.CANCELLED);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<OrderStates, OrderEvents> transitions) throws Exception {
        transitions
                // SUBMITTED -> PAYMENT_PENDING
                .withExternal()
                .source(OrderStates.SUBMITTED).target(OrderStates.PAYMENT_PENDING).event(OrderEvents.PAY)
                .and()
                // PAYMENT_PENDING -> PAID
                .withExternal()
                .source(OrderStates.PAYMENT_PENDING).target(OrderStates.PAID).event(OrderEvents.PAYMENT_SUCCESS)
                .and()
                // PAYMENT_PENDING -> CANCELLED (on failure)
                .withExternal()
                .source(OrderStates.PAYMENT_PENDING).target(OrderStates.CANCELLED).event(OrderEvents.PAYMENT_FAILED)
                .and()
                // PAID -> PREPARING
                .withExternal()
                .source(OrderStates.PAID).target(OrderStates.PREPARING).event(OrderEvents.START_PREPARING)
                .and()
                // PREPARING -> SHIPPED
                .withExternal()
                .source(OrderStates.PREPARING).target(OrderStates.SHIPPED).event(OrderEvents.DISPATCH)
                .and()
                // SHIPPED -> DELIVERED
                .withExternal()
                .source(OrderStates.SHIPPED).target(OrderStates.DELIVERED).event(OrderEvents.DELIVER)
                .and()
                // Any cancellable state -> CANCELLED
                .withExternal()
                .source(OrderStates.SUBMITTED).target(OrderStates.CANCELLED).event(OrderEvents.CANCEL)
                .and()
                .withExternal()
                .source(OrderStates.PAID).target(OrderStates.CANCELLED).event(OrderEvents.CANCEL);
    }
}
