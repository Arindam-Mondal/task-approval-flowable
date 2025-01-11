package com.task.flowable.taskapprovalflowable.controller;

import com.task.flowable.taskapprovalflowable.model.Task;
import com.task.flowable.taskapprovalflowable.service.FlowableTaskService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final FlowableTaskService flowableTaskService;
    private static final Logger logger = LoggerFactory.getLogger(WorkflowController.class);

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startProcess(@RequestBody Task task) {
        logger.info("Starting process");
        Map<String, Object> response = flowableTaskService.startProcessWithTask(task);
        logger.info("Process started with process id {} and task id {}", response.get("processInstanceId"), response.get("taskId"));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/process/{processInstanceId}/state")
    public ResponseEntity<String> getProcessState(@PathVariable String processInstanceId) {
        logger.info("Fetching state for process {}", processInstanceId);
        String state = flowableTaskService.getProcessState(processInstanceId);
        return ResponseEntity.ok(state);
    }

    @PostMapping("/tasks/{taskId}")
    public ResponseEntity<Void> updateTaskStatus(
        @PathVariable Long taskId,
        @RequestBody Task taskModel) {

        flowableTaskService.updateTaskStatus(taskId, taskModel);
        return ResponseEntity.ok().build();
    }

}
