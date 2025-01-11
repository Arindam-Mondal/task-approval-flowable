package com.task.flowable.taskapprovalflowable.delegate;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationDelegate implements JavaDelegate  {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {

        String state = String.valueOf(execution.getVariable("state"));
        Long taskId = (Long) execution.getVariable("taskId");

        logger.info("Sending email to notify user that task {} status updated to {}", taskId, state);
    }
}
