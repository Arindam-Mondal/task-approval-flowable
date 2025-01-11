package com.task.flowable.taskapprovalflowable.listener;

import com.task.flowable.taskapprovalflowable.exception.TaskNotFoundException;
import com.task.flowable.taskapprovalflowable.model.Task;
import com.task.flowable.taskapprovalflowable.model.TaskState;
import com.task.flowable.taskapprovalflowable.repository.TaskRepository;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessStartListener implements ExecutionListener {

    @Autowired
    private TaskRepository taskRepository;

    // Add default constructor
    public ProcessStartListener() {
    }

    @Override
    public void notify(DelegateExecution execution) {
        Long taskId = (Long) execution.getVariable("taskId");
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new TaskNotFoundException("Task not found: " + taskId));

        task.setState(TaskState.DRAFT);
        taskRepository.save(task);
    }
}

