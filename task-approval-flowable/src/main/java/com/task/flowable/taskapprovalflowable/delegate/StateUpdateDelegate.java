package com.task.flowable.taskapprovalflowable.delegate;

import com.task.flowable.taskapprovalflowable.exception.RecordNotFoundException;
import com.task.flowable.taskapprovalflowable.model.Record;
import com.task.flowable.taskapprovalflowable.model.RecordState;
import com.task.flowable.taskapprovalflowable.repository.RecordRepository;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class StateUpdateDelegate implements JavaDelegate {

    @Autowired
    private RecordRepository recordRepository;

    private static final Logger logger = LoggerFactory.getLogger(StateUpdateDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {

        String state = String.valueOf(execution.getVariable("state"));
        Long recordId = (Long) execution.getVariable("recordId");

        Record record = recordRepository.findById(recordId)
            .orElseThrow(() -> new RecordNotFoundException("Record not found: " + recordId));

        logger.info("Updating record state to {} for record {}", state, recordId);

        record.setState(RecordState.valueOf(state));
        record.setLastModifiedAt(LocalDateTime.now());

        // Update comments if available
        String comments = (String) execution.getVariable("comments");
        if (comments != null) {
            record.setComments(comments);
        }

        recordRepository.save(record);
    }
}
