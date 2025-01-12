package com.task.flowable.taskapprovalflowable.service;

import com.task.flowable.taskapprovalflowable.dto.ActivityInstanceDTO;
import com.task.flowable.taskapprovalflowable.dto.ProcessInstanceDTO;
import com.task.flowable.taskapprovalflowable.exception.RecordNotFoundException;
import lombok.AllArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ActivityInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ProcessService {
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private static final String SEQUENCE_FLOW = "sequenceFlow";

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
