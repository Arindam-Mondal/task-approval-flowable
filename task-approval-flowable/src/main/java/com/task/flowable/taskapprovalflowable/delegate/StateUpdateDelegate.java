package com.task.flowable.taskapprovalflowable.delegate;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StateUpdateDelegate implements JavaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(StateUpdateDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {

        String state = String.valueOf(execution.getVariable("state"));
        Long recordId = (Long) execution.getVariable("recordId");

        logger.info("Updating record state to {} for record {}", state, recordId);

    }
}
