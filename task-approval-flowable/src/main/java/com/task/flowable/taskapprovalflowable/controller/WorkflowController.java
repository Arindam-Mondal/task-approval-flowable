package com.task.flowable.taskapprovalflowable.controller;

import com.task.flowable.taskapprovalflowable.model.Record;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final FlowableTaskService flowableTaskService;
    private static final Logger logger = LoggerFactory.getLogger(WorkflowController.class);

    @PostMapping("/start/{recordId}")
    public ResponseEntity<Map<String, Object>> startProcess(@RequestBody Record recordModel, @PathVariable Long recordId) {
        logger.info("Starting process");
        recordModel.setId(recordId);
        Map<String, Object> response = flowableTaskService.startProcessWithRecord(recordModel);
        logger.info("Process started with process id {} and record id {}", response.get("processInstanceId"), response.get("recordId"));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/process/{processInstanceId}/state")
    public ResponseEntity<String> getProcessState(@PathVariable String processInstanceId) {
        logger.info("Fetching state for process {}", processInstanceId);
        String state = flowableTaskService.getProcessState(processInstanceId);
        return ResponseEntity.ok(state);
    }

    @PostMapping("/{recordId}")
    public ResponseEntity<Void> updateRecordStatus(
        @PathVariable Long recordId,
        @RequestBody Record recordModel) {

        flowableTaskService.updateRecordStatus(recordId, recordModel);
        return ResponseEntity.ok().build();
    }

    /**
     * Get all completed process instance IDs
     * @return List of completed process instance IDs
     */
    @GetMapping("/process/all")
    public ResponseEntity<List<String>> getCompletedProcessInstanceIds() {
        return ResponseEntity.ok(flowableTaskService.getAllProcess());
    }

}
