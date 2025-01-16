package com.task.flowable.taskapprovalflowable.delegate;

import com.task.flowable.taskapprovalflowable.exception.InvalidStatusException;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvalidStateErrorDelegate implements JavaDelegate {
    private static final Logger logger = LoggerFactory.getLogger(InvalidStateErrorDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        String workflowState = String.valueOf(execution.getVariable("workflowState"));
        String state = String.valueOf(execution.getVariable("state"));
        logger.error("Invalid state transition attempted: workflowState={}, state={}", workflowState, state);
        throw new InvalidStatusException("Invalid state: Please provide a valid state");
    }
}
