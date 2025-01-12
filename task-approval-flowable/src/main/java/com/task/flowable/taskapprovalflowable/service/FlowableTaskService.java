package com.task.flowable.taskapprovalflowable.service;


import com.task.flowable.taskapprovalflowable.exception.DuplicateRecordException;
import com.task.flowable.taskapprovalflowable.exception.RecordNotFoundException;
import com.task.flowable.taskapprovalflowable.model.Record;
import com.task.flowable.taskapprovalflowable.model.RecordState;
import com.task.flowable.taskapprovalflowable.repository.RecordRepository;
import lombok.AllArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class FlowableTaskService {

    private final static String DRAFT_TASK = "draftTask";
    private final static String REVIEW_TASK = "reviewTask";
    private final static String APPROVE_TASK = "approveTask";

    @Autowired
    private HistoryService historyService;

    private final RecordRepository recordRepository;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    // Start a new process with a Record
    public Map<String, Object> startProcessWithTask(Record record) {

        recordRepository.findById(record.getId())
            .ifPresent(taskObject -> {
                throw new DuplicateRecordException("Duplicate record with ID: " + record.getId());
            });

        Record savedRecord = recordRepository.save(record);
        Map<String, Object> variables = new HashMap<>();
        variables.put("recordId", savedRecord.getId());
        variables.put("initiator", record.getCreatedBy());

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
            "taskApprovalProcess",
            variables
        );

        Map<String, Object> response = new HashMap<>();
        response.put("processInstanceId", processInstance.getId());
        response.put("recordId", savedRecord.getId());

        return response;
    }

    // Get the state of a process instance
    public String getProcessState(String processInstanceId) {
        // Retrieve the process instance
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();

        if (processInstance == null) {
            return "Process instance not found";
        }

        // Get active activities for the running process instance
        List<String> activeActivities = runtimeService.getActiveActivityIds(processInstance.getId());

        if (activeActivities.isEmpty()) {
            return "Process has completed";
        }

        return "Active activities: " + String.join(", ", activeActivities);
    }

    // Update the task status and complete the associated process task
    public void updateTaskStatus(Long recordId, Record taskModel) {

        Record taskObject = recordRepository.findById(recordId)
            .orElseThrow(() -> new RecordNotFoundException("Record not found: " + recordId));

        if (taskModel.getState() == RecordState.DOCUMENT_READY_FOR_REVIEW) {
            updateTaskState(recordId, taskModel, taskObject, DRAFT_TASK);
        }else if(taskModel.getState() == RecordState.REVIEW_ACCEPTED || taskModel.getState() == RecordState.REVIEW_REJECTED) {
            updateTaskState(recordId, taskModel, taskObject, REVIEW_TASK);
        } else if(taskModel.getState() == RecordState.APPROVAL_ACCEPTED || taskModel.getState() == RecordState.APPROVAL_REJECTED) {
            updateTaskState(recordId, taskModel, taskObject, APPROVE_TASK);
        }

    }

    private void updateTaskState(Long recordId, Record taskModel, Record taskObject, String taskDefinitionKey) {

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

    public List<String> getAllProcess() {
        // Create the query to fetch completed processes
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery()
            .finished();  // This filters for completed processes

        // Execute the query and get the list of HistoricProcessInstances
        List<HistoricProcessInstance> completedProcesses = query.list();

        // Extract the process instance IDs
        return completedProcesses.stream()
            .map(HistoricProcessInstance::getId)
            .toList();
    }

}
