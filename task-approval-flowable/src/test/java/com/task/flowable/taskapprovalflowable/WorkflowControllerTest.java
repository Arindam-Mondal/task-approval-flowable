package com.task.flowable.taskapprovalflowable;


import com.task.flowable.taskapprovalflowable.controller.WorkflowController;
import com.task.flowable.taskapprovalflowable.dto.WorkflowDTO;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.test.Deployment;
import org.flowable.spring.impl.test.FlowableSpringExtension;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(FlowableSpringExtension.class)
@SpringBootTest
class WorkflowControllerTest {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private WorkflowController workflowController;

    @Test
    @Deployment(resources = { "processes/task-approval-process.bpmn20.xml" })
    void process() {

        workflowController.updateRecordStatus(8979L,null);

        var response = workflowController.getRecordState(8979L);

        assertEquals(String.valueOf("INIT"), String.valueOf(response.getBody().get("workflowState")));

        WorkflowDTO workflowDTO = WorkflowDTO.builder()
            .workflowState(WorkflowDTO.WorkflowState.DOCUMENT_READY_FOR_REVIEW)
            .state(WorkflowDTO.State.DRAFTED)
            .build();

        workflowController.updateRecordStatus(8979L,workflowDTO);

        response = workflowController.getRecordState(8979L);

        assertEquals(String.valueOf("DOCUMENT_READY_FOR_REVIEW"), String.valueOf(response.getBody().get("workflowState")));
        assertEquals(String.valueOf("DRAFTED"), String.valueOf(response.getBody().get("state")));

        workflowDTO = WorkflowDTO.builder()
            .workflowState(WorkflowDTO.WorkflowState.REVIEW_ACCEPTED)
            .state(WorkflowDTO.State.DRAFTED)
            .build();

        workflowController.updateRecordStatus(8979L,workflowDTO);

        response = workflowController.getRecordState(8979L);

        assertEquals(String.valueOf("REVIEW_ACCEPTED"), String.valueOf(response.getBody().get("workflowState")));
        assertEquals(String.valueOf("REVIEWED"), String.valueOf(response.getBody().get("state")));

        workflowDTO = WorkflowDTO.builder()
            .workflowState(WorkflowDTO.WorkflowState.APPROVAL_ACCEPTED)
            .state(WorkflowDTO.State.REVIEWED)
            .build();

        workflowController.updateRecordStatus(8979L,workflowDTO);

        response = workflowController.getRecordState(8979L);

        assertEquals(String.valueOf("APPROVAL_ACCEPTED"), String.valueOf(response.getBody().get("workflowState")));
        assertEquals(String.valueOf("SIGNED"), String.valueOf(response.getBody().get("state")));
    }

}