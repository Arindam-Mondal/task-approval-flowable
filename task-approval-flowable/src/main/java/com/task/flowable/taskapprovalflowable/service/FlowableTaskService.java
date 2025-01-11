package com.task.flowable.taskapprovalflowable.service;


import com.task.flowable.taskapprovalflowable.model.Task;
import com.task.flowable.taskapprovalflowable.model.TaskState;
import com.task.flowable.taskapprovalflowable.repository.TaskRepository;
import lombok.AllArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
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

    private final TaskRepository taskRepository;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    // Start a new process with a Task
    public Map<String, Object> startProcessWithTask(Task task) {
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
            .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

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

        org.flowable.task.api.Task draftTask = taskService.createTaskQuery()
            .taskDefinitionKey(taskDefinitionKey)
            .processVariableValueEquals("taskId", taskId)
            .singleResult();

        Map<String, Object> variables = new HashMap<>();
        variables.put("state", state);
        variables.put("taskId", taskId);
        variables.put("approved", isApproved);

        taskService.complete(draftTask.getId(), variables);
        taskObject.setState(taskModel.getState());
    }

}
