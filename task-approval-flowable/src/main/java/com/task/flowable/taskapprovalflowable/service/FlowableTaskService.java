package com.task.flowable.taskapprovalflowable.service;


import com.task.flowable.taskapprovalflowable.dto.ProcessInstanceDTO;
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

import java.util.ArrayList;
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

    public List<ProcessInstanceDTO> getAllProcessInstances() {
        List<ProcessInstanceDTO> allProcesses = new ArrayList<>();

        // Get historic processes (both finished and unfinished)
        List<HistoricProcessInstance> historicProcesses = historyService.createHistoricProcessInstanceQuery()
            .orderByProcessInstanceStartTime()
            .desc()
            .list();

        // Get current running processes
        List<ProcessInstance> runningProcesses = runtimeService.createProcessInstanceQuery()
            .active()
            .list();

        // Map historic processes
        for (HistoricProcessInstance historicProcess : historicProcesses) {
            String state;
            if (historicProcess.getEndTime() != null) {
                state = "COMPLETED";
            } else {
                // Check if it's still running
                ProcessInstance runningInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(historicProcess.getId())
                    .singleResult();
                if (runningInstance != null) {
                    state = runningInstance.isSuspended() ? "SUSPENDED" : "ACTIVE";
                } else {
                    state = "TERMINATED";
                }
            }

            allProcesses.add(new ProcessInstanceDTO(
                historicProcess.getProcessDefinitionId(),
                historicProcess.getId(),
                state
            ));
        }

        // Add any running processes that might not be in history yet
        for (ProcessInstance runningProcess : runningProcesses) {
            // Check if we already added this process from history
            boolean exists = allProcesses.stream()
                .anyMatch(p -> p.getInstanceId().equals(runningProcess.getId()));

            if (!exists) {
                allProcesses.add(new ProcessInstanceDTO(
                    runningProcess.getProcessDefinitionId(),
                    runningProcess.getId(),
                    runningProcess.isSuspended() ? "SUSPENDED" : "ACTIVE"
                ));
            }
        }

        return allProcesses;
    }

}
