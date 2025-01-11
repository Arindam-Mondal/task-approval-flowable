package com.task.flowable.taskapprovalflowable.delegate;

import com.task.flowable.taskapprovalflowable.model.Task;
import com.task.flowable.taskapprovalflowable.model.TaskState;
import com.task.flowable.taskapprovalflowable.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

//TODO : Not using now, but can be used
@Component
public class SignDocumentDelegate implements JavaDelegate {

    @Autowired
    private TaskRepository taskRepository;

    @Override
    public void execute(DelegateExecution execution) {
        Long taskId = (Long) execution.getVariable("taskId");
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        // Add signing logic here
        task.setState(TaskState.SIGNED);
        // For example: digital signature, timestamp, etc.
        task.setComments(task.getComments() + "\nDocument signed on: " + LocalDateTime.now());
        taskRepository.save(task);
    }
}
