package com.task.flowable.taskapprovalflowable.listener;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProcessStartListener implements ExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(ProcessStartListener.class);

    // Add default constructor
    public ProcessStartListener() {
    }

    @Override
    public void notify(DelegateExecution execution) {
        Long recordId = (Long) execution.getVariable("recordId");
        logger.info("Process Initiated for record {}", recordId);
    }
}

