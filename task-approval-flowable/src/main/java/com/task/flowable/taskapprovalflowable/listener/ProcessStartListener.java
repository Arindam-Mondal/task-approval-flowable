package com.task.flowable.taskapprovalflowable.listener;

import com.task.flowable.taskapprovalflowable.exception.RecordNotFoundException;
import com.task.flowable.taskapprovalflowable.model.Record;
import com.task.flowable.taskapprovalflowable.model.RecordState;
import com.task.flowable.taskapprovalflowable.repository.RecordRepository;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessStartListener implements ExecutionListener {

    @Autowired
    private RecordRepository recordRepository;

    // Add default constructor
    public ProcessStartListener() {
    }

    @Override
    public void notify(DelegateExecution execution) {
        Long recordId = (Long) execution.getVariable("recordId");
        Record record = recordRepository.findById(recordId)
            .orElseThrow(() -> new RecordNotFoundException("Record not found: " + recordId));

        record.setState(RecordState.DRAFT);
        recordRepository.save(record);
    }
}

