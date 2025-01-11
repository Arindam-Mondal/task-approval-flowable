package com.task.flowable.taskapprovalflowable.delegate;

import com.task.flowable.taskapprovalflowable.controller.WorkflowController;
import com.task.flowable.taskapprovalflowable.model.Task;
import com.task.flowable.taskapprovalflowable.model.TaskState;
import com.task.flowable.taskapprovalflowable.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class StateUpdateDelegate implements JavaDelegate {

    @Autowired
    private TaskRepository taskRepository;

    private static final Logger logger = LoggerFactory.getLogger(StateUpdateDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {

        String state = String.valueOf(execution.getVariable("state"));
        Long taskId = (Long) execution.getVariable("taskId");

        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        logger.info("Updating task state to {} for task {}", state, taskId);

        task.setState(TaskState.valueOf(state));
        task.setLastModifiedAt(LocalDateTime.now());

        // Update comments if available
        String comments = (String) execution.getVariable("comments");
        if (comments != null) {
            task.setComments(comments);
        }

        taskRepository.save(task);
    }
}
