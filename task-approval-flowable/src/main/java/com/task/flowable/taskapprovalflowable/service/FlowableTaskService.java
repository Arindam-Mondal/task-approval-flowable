package com.task.flowable.taskapprovalflowable.service;


import com.task.flowable.taskapprovalflowable.exception.DuplicateRecordException;
import com.task.flowable.taskapprovalflowable.exception.RecordNotFoundException;
import com.task.flowable.taskapprovalflowable.model.Record;
import com.task.flowable.taskapprovalflowable.model.RecordState;
import com.task.flowable.taskapprovalflowable.repository.RecordRepository;
import lombok.AllArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@AllArgsConstructor
public class FlowableTaskService {

    private final static String DRAFT_TASK = "draftTask";
    private final static String REVIEW_TASK = "reviewTask";
    private final static String APPROVE_TASK = "approveTask";

    private final RecordRepository recordRepository;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    // Start a new process with a Record
    public Map<String, Object> startProcessWithRecord(Record record) {

        recordRepository.findById(record.getId())
            .ifPresent(recordObject -> {
                throw new DuplicateRecordException("Duplicate record with ID: " + recordObject.getId() +" is already associated to process " + recordObject.getProcessInstanceId());
            });


        Map<String, Object> variables = new HashMap<>();
        variables.put("recordId", record.getId());
        variables.put("initiator", record.getCreatedBy());

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
            "taskApprovalProcess",
            variables
        );

        Map<String, Object> response = new HashMap<>();
        response.put("processInstanceId", processInstance.getId());
        response.put("recordId", record.getId());

        record.setProcessInstanceId(processInstance.getId());

        recordRepository.save(record);

        return response;
    }

    // Update the task status and complete the associated process task
    public void updateRecordStatus(Long recordId, Record taskModel) {

        Record taskObject = recordRepository.findById(recordId)
            .orElseThrow(() -> new RecordNotFoundException("Record not found: " + recordId));

        if (taskModel.getState() == RecordState.DOCUMENT_READY_FOR_REVIEW) {
            updateRecordState(recordId, taskModel, taskObject, DRAFT_TASK);
        }else if(taskModel.getState() == RecordState.REVIEW_ACCEPTED || taskModel.getState() == RecordState.REVIEW_REJECTED) {
            updateRecordState(recordId, taskModel, taskObject, REVIEW_TASK);
        } else if(taskModel.getState() == RecordState.APPROVAL_ACCEPTED || taskModel.getState() == RecordState.APPROVAL_REJECTED) {
            updateRecordState(recordId, taskModel, taskObject, APPROVE_TASK);
        }

    }

    private void updateRecordState(Long recordId, Record taskModel, Record taskObject, String taskDefinitionKey) {

        boolean isApproved = taskModel.getState() == RecordState.REVIEW_ACCEPTED || taskModel.getState() == RecordState.APPROVAL_ACCEPTED;

        RecordState state = taskModel.getState();

        if(state == RecordState.APPROVAL_ACCEPTED) {
            state = RecordState.SIGNED;
        } else if(state == RecordState.REVIEW_REJECTED || state == RecordState.APPROVAL_REJECTED) {
            state = RecordState.DRAFT;
        }

        org.flowable.task.api.Task task = getTask(recordId, taskDefinitionKey);

        Map<String, Object> variables = new HashMap<>();
        variables.put("state", state);
        variables.put("recordId", recordId);
        variables.put("approved", isApproved);

        taskService.complete(task.getId(), variables);
        taskObject.setState(taskModel.getState());
    }

    private org.flowable.task.api.Task getTask(Long recordId, String taskDefinitionKey) {
        return taskService.createTaskQuery()
            .taskDefinitionKey(taskDefinitionKey)
            .processVariableValueEquals("recordId", recordId)
            .singleResult();
    }

}
