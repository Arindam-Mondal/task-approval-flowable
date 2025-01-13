package com.task.flowable.taskapprovalflowable.delegate;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

//TODO : Not using now, but can be used
@Component
public class SignDocumentDelegate implements JavaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(SignDocumentDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        Long recordId = (Long) execution.getVariable("recordId");
        logger.info("Signing document for record {}", recordId);
    }
}
