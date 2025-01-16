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

        // Retrieve the variables from the execution context
        String currentState = String.valueOf(execution.getVariable("state"));
        Long recordId = (Long) execution.getVariable("recordId");
        String workflowState = String.valueOf(execution.getVariable("workflowState")); // Assuming 'workflowState' is set

        // Log the current state and record ID for debugging
        logger.info("Updating record state from '{}' to '{}' for record {}", currentState, workflowState, recordId);

        // Your state update logic, just as an example
        String updatedState = determineUpdatedState(workflowState, currentState);

        // Set the updated state variable
        execution.setVariable("state", updatedState);  // This updates the 'state' process variable
        logger.info("Updated state to '{}' for record {}", updatedState, recordId);


    }

    private String determineUpdatedState(String workflowState, String currentState) {
        if ("APPROVAL_ACCEPTED".equals(workflowState)) {
            return "SIGNED";
        } else if ("REVIEW_ACCEPTED".equals(workflowState)) {
            return "REVIEWED";
        } else if ("REVIEW_REJECTED".equals(workflowState) || "APPROVAL_REJECTED".equals(workflowState)) {
            return "DRAFTED";
        }
        // Return current state if no change is needed
        return currentState;
    }
}
