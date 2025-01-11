package com.task.flowable.taskapprovalflowable.service;


import com.task.flowable.taskapprovalflowable.exception.DuplicateTaskException;
import com.task.flowable.taskapprovalflowable.exception.TaskNotFoundException;
import com.task.flowable.taskapprovalflowable.model.Task;
import com.task.flowable.taskapprovalflowable.model.TaskState;
import com.task.flowable.taskapprovalflowable.repository.TaskRepository;
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

    private final TaskRepository taskRepository;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    // Start a new process with a Task
    public Map<String, Object> startProcessWithTask(Task task) {

        taskRepository.findById(task.getId())
            .ifPresent(taskObject -> {
                throw new DuplicateTaskException("Duplicate task with ID: " + task.getId());
            });

        Task savedTask = taskRepository.save(task);
        Map<String, Object> variables = new HashMap<>();
        variables.put("taskId", savedTask.getId());
        variables.put("initiator", task.getCreatedBy());

        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
            "taskApprovalProcess",
            variables
        );

        Map<String, Object> response = new HashMap<>();
        response.put("processInstanceId", processInstance.getId());
        response.put("taskId", savedTask.getId());

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
    public void updateTaskStatus(Long taskId, Task taskModel) {

        Task taskObject = taskRepository.findById(taskId)
            .orElseThrow(() -> new TaskNotFoundException("Task not found: " + taskId));

        if (taskModel.getState() == TaskState.DOCUMENT_READY_FOR_REVIEW) {
            updateTaskState(taskId, taskModel, taskObject, DRAFT_TASK);
        }else if(taskModel.getState() == TaskState.REVIEW_ACCEPTED || taskModel.getState() == TaskState.REVIEW_REJECTED) {
            updateTaskState(taskId, taskModel, taskObject, REVIEW_TASK);
        } else if(taskModel.getState() == TaskState.APPROVAL_ACCEPTED || taskModel.getState() == TaskState.APPROVAL_REJECTED) {
            updateTaskState(taskId, taskModel, taskObject, APPROVE_TASK);
        }

    }

    private void updateTaskState(Long taskId, Task taskModel, Task taskObject, String taskDefinitionKey) {

        boolean isApproved = taskModel.getState() == TaskState.REVIEW_ACCEPTED || taskModel.getState() == TaskState.APPROVAL_ACCEPTED;

        TaskState state = taskModel.getState();

        if(state == TaskState.APPROVAL_ACCEPTED) {
            state = TaskState.SIGNED;
        } else if(state == TaskState.REVIEW_REJECTED || state == TaskState.APPROVAL_REJECTED) {
            state = TaskState.DRAFT;
        }

        org.flowable.task.api.Task task = getTask(taskId, taskDefinitionKey);

        Map<String, Object> variables = new HashMap<>();
        variables.put("state", state);
        variables.put("taskId", taskId);
        variables.put("approved", isApproved);

        taskService.complete(task.getId(), variables);
        taskObject.setState(taskModel.getState());
    }

    private org.flowable.task.api.Task getTask(Long taskId, String taskDefinitionKey) {
        return taskService.createTaskQuery()
            .taskDefinitionKey(taskDefinitionKey)
            .processVariableValueEquals("taskId", taskId)
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
