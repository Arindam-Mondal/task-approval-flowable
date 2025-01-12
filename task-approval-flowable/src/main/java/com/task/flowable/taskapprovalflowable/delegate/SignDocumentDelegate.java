package com.task.flowable.taskapprovalflowable.delegate;

import com.task.flowable.taskapprovalflowable.exception.RecordNotFoundException;
import com.task.flowable.taskapprovalflowable.model.Record;
import com.task.flowable.taskapprovalflowable.model.RecordState;
import com.task.flowable.taskapprovalflowable.repository.RecordRepository;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

//TODO : Not using now, but can be used
@Component
public class SignDocumentDelegate implements JavaDelegate {

    @Autowired
    private RecordRepository recordRepository;

    @Override
    public void execute(DelegateExecution execution) {
        Long recordId = (Long) execution.getVariable("recordId");
        Record task = recordRepository.findById(recordId)
            .orElseThrow(() -> new RecordNotFoundException("Record not found: " + recordId));

        // Add signing logic here
        task.setState(RecordState.SIGNED);
        // For example: digital signature, timestamp, etc.
        task.setComments(task.getComments() + "\nDocument signed on: " + LocalDateTime.now());
        recordRepository.save(task);
    }
}
