package com.task.flowable.taskapprovalflowable.controller;

import com.task.flowable.taskapprovalflowable.dto.WorkflowDTO;
import com.task.flowable.taskapprovalflowable.exception.DataCorruptionException;
import com.task.flowable.taskapprovalflowable.exception.DuplicateRecordException;
import com.task.flowable.taskapprovalflowable.exception.InvalidStatusException;
import com.task.flowable.taskapprovalflowable.exception.ProcessingException;
import com.task.flowable.taskapprovalflowable.exception.RecordNotFoundException;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowController {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowController.class);

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;


    @PostMapping("/{recordId}")
    public ResponseEntity<Map<String, Object>> updateRecordStatus(
        @PathVariable Long recordId,
        @RequestBody(required = false) WorkflowDTO workflowDTO) {

        if (workflowDTO == null || isEmpty(workflowDTO)) {
            Map<String, Object> response = startProcess(recordId);
            logger.info("Process started with process id {} and record id {}", response.get("processInstanceId"), response.get("recordId"));
            return ResponseEntity.ok(response);
        }

        updateWorkflowStatus(recordId, workflowDTO);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{recordId}")
    public ResponseEntity<Map<String, Object>> getRecordState(
        @PathVariable Long recordId) {

        String businessKey = String.valueOf(recordId);
        Map<String, Object> response = new HashMap<>();

        try {
            // First check active processes
            ProcessInstance activeProcess = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .singleResult();

            if (activeProcess != null) {
                response.put("processInstanceId", activeProcess.getId());
                response.put("status", "ACTIVE");

                // Get process variables
                Map<String, Object> variables = runtimeService.getVariables(activeProcess.getId());
                if (variables.containsKey("state")) {
                    response.put("state", variables.get("state"));
                }
                if (variables.containsKey("workflowState")) {
                    response.put("workflowState", variables.get("workflowState"));
                }

                return ResponseEntity.ok(response);
            }

            // If no active process, check historic processes
            HistoricProcessInstance historicProcess = historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .orderByProcessInstanceEndTime()
                .desc()
                .singleResult();

            if (historicProcess != null) {
                response.put("processInstanceId", historicProcess.getId());
                response.put("status", "COMPLETED");
                response.put("endTime", historicProcess.getEndTime());

                // Get historic variables
                List<HistoricVariableInstance> historicVariables = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(historicProcess.getId())
                    .list();

                Map<String, Object> variables = historicVariables.stream()
                    .collect(Collectors.toMap(
                        HistoricVariableInstance::getVariableName,
                        HistoricVariableInstance::getValue
                    ));

                if (variables.containsKey("state")) {
                    response.put("state", variables.get("state"));
                }
                if (variables.containsKey("workflowState")) {
                    response.put("workflowState", variables.get("workflowState"));
                }

                return ResponseEntity.ok(response);
            }

            // No process found
            return ResponseEntity.notFound().build();

        } catch (FlowableException e) {
            logger.error("Error retrieving process state for record {}: {}", recordId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Collections.singletonMap("error",
                    "Error retrieving process state. Please contact administrator."));
        }
    }


    // Start a new process with a Record
    private Map<String, Object> startProcess(Long recordId) {

        String businessKey = String.valueOf(recordId);

        try {
            // Check active process instances
            List<ProcessInstance> activeProcesses = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .list();

            // Check historic process instances
            List<HistoricProcessInstance> historicProcesses = historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .list();

            // Handle multiple active processes (data corruption scenario)
            if (activeProcesses.size() > 1) {
                String processIds = activeProcesses.stream()
                    .map(ProcessInstance::getProcessInstanceId)
                    .collect(Collectors.joining(", "));

                logger.error("Data corruption detected: Multiple active processes found for business key {}: {}",
                    businessKey, processIds);
                throw new DataCorruptionException("Multiple active processes found for record ID: " + recordId +
                    ". Please contact system administrator. Reference: " + processIds);
            }

            // Handle multiple historic processes (data corruption scenario)
            if (historicProcesses.size() > 1) {
                String processIds = historicProcesses.stream()
                    .map(HistoricProcessInstance::getId)
                    .collect(Collectors.joining(", "));

                logger.error("Data corruption detected: Multiple historic processes found for business key {}: {}",
                    businessKey, processIds);
                throw new DataCorruptionException("Multiple historic processes found for record ID: " + recordId +
                    ". Please contact system administrator. Reference: " + processIds);
            }

            // Check for single active process
            if (!activeProcesses.isEmpty()) {
                ProcessInstance existingProcess = activeProcesses.get(0);
                throw new DuplicateRecordException("Duplicate record with ID: " + recordId +
                    " is already associated to active process " + existingProcess.getProcessInstanceId());
            }

            // Check for single historic process
            if (!historicProcesses.isEmpty()) {
                HistoricProcessInstance historicProcess = historicProcesses.get(0);
                throw new DuplicateRecordException("Record with ID: " + recordId +
                    " was previously processed in historic process " + historicProcess.getId());
            }

            // Proceed with process creation if no duplicates found
            Map<String, Object> variables = new HashMap<>();
            variables.put("recordId", recordId);
            variables.put("workflowState", WorkflowDTO.WorkflowState.INIT);

            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                "taskApprovalProcess",
                businessKey,
                variables
            );

            Map<String, Object> response = new HashMap<>();
            response.put("processInstanceId", processInstance.getId());
            response.put("recordId", recordId);

            //response
            return response;

        } catch (FlowableException e) {
            logger.error("Flowable error while processing business key {}: {}", businessKey, e.getMessage(), e);
            throw new ProcessingException("Error processing record ID: " + recordId +
                ". Please contact system administrator.", e);
        } catch (Exception e) {
            logger.error("Unexpected error while processing business key {}: {}", businessKey, e.getMessage(), e);
            throw new ProcessingException("Unexpected error processing record ID: " + recordId +
                ". Please contact system administrator.", e);
        }
    }

    // Update the task status and complete the associated process task
    private void updateWorkflowStatus(Long recordId, WorkflowDTO workflowDTO) {

        String businessKey = String.valueOf(recordId);

        ProcessInstance existingProcess = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(businessKey)
            .singleResult();

        Optional.ofNullable(existingProcess)
            .orElseThrow(() -> new RecordNotFoundException("Record not found: " + recordId));

        // Get current task
        org.flowable.task.api.Task currentTask = taskService.createTaskQuery()
            .processInstanceId(existingProcess.getId())
            .singleResult();

        if (currentTask == null) {
            throw new InvalidStatusException("No active task found for record: " + recordId);
        }

        // Prepare variables for the workflow
        Map<String, Object> variables = new HashMap<>();
        variables.put("state", workflowDTO.getState());
        variables.put("workflowState", workflowDTO.getWorkflowState());
        variables.put("recordId", recordId);
        variables.put("approved", isApproved(workflowDTO.getWorkflowState()));

        // Complete the current task with variables
        taskService.complete(currentTask.getId(), variables);

    }

    private boolean isApproved(WorkflowDTO.WorkflowState workflowState) {
        return workflowState == WorkflowDTO.WorkflowState.REVIEW_ACCEPTED ||
            workflowState == WorkflowDTO.WorkflowState.APPROVAL_ACCEPTED;
    }

}
