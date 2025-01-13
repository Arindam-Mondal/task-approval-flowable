package com.task.flowable.taskapprovalflowable.controller;

import com.task.flowable.taskapprovalflowable.dto.ActivityInstanceDTO;
import com.task.flowable.taskapprovalflowable.dto.ProcessInstanceDTO;
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
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ActivityInstance;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowController.class);

    private final static String DRAFT_TASK = "draftTask";
    private final static String REVIEW_TASK = "reviewTask";
    private final static String APPROVE_TASK = "approveTask";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    @GetMapping("/process/{processInstanceId}/state")
    public ResponseEntity<String> getProcessState(@PathVariable String processInstanceId) {
        logger.info("Fetching state for process {}", processInstanceId);
        String state = getWorkflowProcessState(processInstanceId);
        return ResponseEntity.ok(state);
    }

    @GetMapping("/process/{processInstanceId}/details")
    public ResponseEntity<ProcessInstanceDTO> getProcessDetailsById(@PathVariable String processInstanceId) {
        logger.info("Fetching state for process {}", processInstanceId);
        ProcessInstanceDTO processInstanceDTO = getProcessInstanceDetails(processInstanceId);
        return ResponseEntity.ok(processInstanceDTO);
    }

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

    /**
     * Get all completed process instance IDs
     * @return List of completed process instance IDs
     */
    @GetMapping("/process/all")
    public ResponseEntity<List<ProcessInstanceDTO>> getCompletedProcessInstanceIds() {
        return ResponseEntity.ok(getAllProcessInstancesWithActivities());
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

        Optional.ofNullable(existingProcess).orElseThrow(() -> new RecordNotFoundException("Record not found: " + recordId));

        if (workflowDTO.getWorkflowState() == WorkflowDTO.WorkflowState.DOCUMENT_READY_FOR_REVIEW
            && workflowDTO.getState() == WorkflowDTO.State.DRAFTED) {
            updateWorkflowStatus(recordId,workflowDTO, DRAFT_TASK);
        }else if((workflowDTO.getWorkflowState() == WorkflowDTO.WorkflowState.REVIEW_ACCEPTED ||
            workflowDTO.getWorkflowState() == WorkflowDTO.WorkflowState.REVIEW_REJECTED)
            && workflowDTO.getState() == WorkflowDTO.State.DRAFTED) {
            updateWorkflowStatus(recordId,workflowDTO, REVIEW_TASK);
        } else if((workflowDTO.getWorkflowState() == WorkflowDTO.WorkflowState.APPROVAL_ACCEPTED ||
            workflowDTO.getWorkflowState() == WorkflowDTO.WorkflowState.APPROVAL_REJECTED)
            && workflowDTO.getState() == WorkflowDTO.State.REVIEWED) {
            updateWorkflowStatus(recordId,workflowDTO, APPROVE_TASK);
        } else {
            throw new InvalidStatusException("Please provide a valid state");
        }

    }

    private void updateWorkflowStatus(Long recordId, WorkflowDTO workflowDTO, String taskDefinitionKey) {

        boolean isApproved = workflowDTO.getWorkflowState() == WorkflowDTO.WorkflowState.REVIEW_ACCEPTED
            || workflowDTO.getWorkflowState() == WorkflowDTO.WorkflowState.APPROVAL_ACCEPTED;

        WorkflowDTO.State state = workflowDTO.getState();

        if(workflowDTO.getWorkflowState() == WorkflowDTO.WorkflowState.APPROVAL_ACCEPTED) {
            state = WorkflowDTO.State.SIGNED;
        } else if(workflowDTO.getWorkflowState() == WorkflowDTO.WorkflowState.REVIEW_REJECTED
            || workflowDTO.getWorkflowState() == WorkflowDTO.WorkflowState.APPROVAL_REJECTED) {
            state = WorkflowDTO.State.DRAFTED;
        }

        org.flowable.task.api.Task task = getTask(recordId, taskDefinitionKey);

        Optional.ofNullable(task).orElseThrow(()-> new InvalidStatusException("Please provide a valid state"));

        Map<String, Object> variables = new HashMap<>();

        variables.put("state", state);
        variables.put("workflowState", workflowDTO.getWorkflowState());
        variables.put("recordId", recordId);
        variables.put("approved", isApproved);

        taskService.complete(task.getId(), variables);
    }

    private org.flowable.task.api.Task getTask(Long recordId, String taskDefinitionKey) {
        return taskService.createTaskQuery()
            .taskDefinitionKey(taskDefinitionKey)
            .processVariableValueEquals("recordId", recordId)
            .singleResult();
    }

    private static final String SEQUENCE_FLOW = "sequenceFlow";

    // Get the state of a process instance
    public String getWorkflowProcessState(String processInstanceId) {
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

    public List<ProcessInstanceDTO> getAllProcessInstancesWithActivities() {
        List<ProcessInstanceDTO> allProcesses = new ArrayList<>();

        // Get historic processes
        List<HistoricProcessInstance> historicProcesses = historyService.createHistoricProcessInstanceQuery()
            .orderByProcessInstanceStartTime()
            .desc()
            .list();

        // Get current running processes
        List<ProcessInstance> runningProcesses = runtimeService.createProcessInstanceQuery()
            .active()
            .list();

        // Process historic instances
        for (HistoricProcessInstance historicProcess : historicProcesses) {
            ProcessInstanceDTO processDTO = createProcessInstanceDTO(historicProcess);
            allProcesses.add(processDTO);
        }

        // Add any running processes that might not be in history yet
        for (ProcessInstance runningProcess : runningProcesses) {
            if (!processExists(allProcesses, runningProcess.getId())) {
                ProcessInstanceDTO processDTO = createProcessInstanceDTOFromRunning(runningProcess);
                allProcesses.add(processDTO);
            }
        }

        return allProcesses;
    }

    public ProcessInstanceDTO getProcessInstanceDetails(String processInstanceId) {
        HistoricProcessInstance historicProcess = findHistoricProcess(processInstanceId);
        return createProcessInstanceDTO(historicProcess);
    }

    // Helper methods
    private ProcessInstanceDTO createProcessInstanceDTO(HistoricProcessInstance historicProcess) {
        String state = determineProcessState(historicProcess);
        List<ActivityInstanceDTO> activities = getActivitiesForProcess(historicProcess.getId(), state);

        return new ProcessInstanceDTO(
            historicProcess.getProcessDefinitionId(),
            historicProcess.getId(),
            state,
            activities
        );
    }

    private ProcessInstanceDTO createProcessInstanceDTOFromRunning(ProcessInstance runningProcess) {
        String state = runningProcess.isSuspended() ? "SUSPENDED" : "ACTIVE";
        List<ActivityInstanceDTO> activities = getCurrentActivities(runningProcess.getId());

        return new ProcessInstanceDTO(
            runningProcess.getProcessDefinitionId(),
            runningProcess.getId(),
            state,
            activities
        );
    }

    private String determineProcessState(HistoricProcessInstance historicProcess) {
        if (historicProcess.getEndTime() != null) {
            return "COMPLETED";
        }

        ProcessInstance runningInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(historicProcess.getId())
            .singleResult();

        if (runningInstance != null) {
            return runningInstance.isSuspended() ? "SUSPENDED" : "ACTIVE";
        }

        return "TERMINATED";
    }

    private List<ActivityInstanceDTO> getActivitiesForProcess(String processId, String processState) {
        List<ActivityInstanceDTO> activities = getHistoricActivities(processId);

        if (processState.equals("ACTIVE") || processState.equals("SUSPENDED")) {
            activities.addAll(getCurrentActivitiesNotInHistory(processId, activities));
        }

        return activities;
    }

    private List<ActivityInstanceDTO> getHistoricActivities(String processId) {
        List<HistoricActivityInstance> historicActivities = historyService.createHistoricActivityInstanceQuery()
            .processInstanceId(processId)
            .orderByHistoricActivityInstanceStartTime()
            .asc()
            .list();

        return historicActivities.stream()
            .filter(activity -> !SEQUENCE_FLOW.equals(activity.getActivityType()))
            .map(this::mapHistoricActivityToDTO)
            .collect(Collectors.toList());
    }

    private ActivityInstanceDTO mapHistoricActivityToDTO(HistoricActivityInstance historicActivity) {
        String activityState = historicActivity.getEndTime() != null ? "COMPLETED" : "ACTIVE";

        return new ActivityInstanceDTO(
            historicActivity.getActivityId(),
            historicActivity.getActivityName(),
            historicActivity.getActivityType(),
            historicActivity.getStartTime(),
            historicActivity.getEndTime(),
            activityState
        );
    }

    private List<ActivityInstanceDTO> getCurrentActivities(String processId) {
        List<ActivityInstance> currentActivities = runtimeService.createActivityInstanceQuery()
            .processInstanceId(processId)
            .list();

        return currentActivities.stream()
            .filter(activity -> !SEQUENCE_FLOW.equals(activity.getActivityType()))
            .map(this::mapCurrentActivityToDTO)
            .collect(Collectors.toList());
    }

    private List<ActivityInstanceDTO> getCurrentActivitiesNotInHistory(
        String processId, List<ActivityInstanceDTO> existingActivities) {
        return getCurrentActivities(processId).stream()
            .filter(activity -> !activityExistsInList(existingActivities, activity.getActivityId()))
            .collect(Collectors.toList());
    }

    private ActivityInstanceDTO mapCurrentActivityToDTO(ActivityInstance activity) {
        return new ActivityInstanceDTO(
            activity.getActivityId(),
            activity.getActivityName(),
            activity.getActivityType(),
            activity.getStartTime(),
            null,
            "ACTIVE"
        );
    }

    private boolean activityExistsInList(List<ActivityInstanceDTO> activities, String activityId) {
        return activities.stream()
            .anyMatch(a -> a.getActivityId().equals(activityId));
    }

    private boolean processExists(List<ProcessInstanceDTO> processes, String processId) {
        return processes.stream()
            .anyMatch(p -> p.getInstanceId().equals(processId));
    }

    private HistoricProcessInstance findHistoricProcess(String processInstanceId) {
        HistoricProcessInstance historicProcess = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();

        if (historicProcess == null) {
            throw new RecordNotFoundException("Process instance not found: " + processInstanceId);
        }

        return historicProcess;
    }

}
