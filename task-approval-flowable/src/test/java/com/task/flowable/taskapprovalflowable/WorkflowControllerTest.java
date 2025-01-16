package com.task.flowable.taskapprovalflowable;


import com.task.flowable.taskapprovalflowable.controller.WorkflowController;
import com.task.flowable.taskapprovalflowable.dto.WorkflowDTO;
import com.task.flowable.taskapprovalflowable.exception.InvalidStatusException;
import com.task.flowable.taskapprovalflowable.exception.ProcessingException;
import com.task.flowable.taskapprovalflowable.exception.RecordNotFoundException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.test.Deployment;
import org.flowable.spring.impl.test.FlowableSpringExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


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

    //After APPROVAL_REJECTED - The workflow should again move to Draft
    @Test
    @Deployment(resources = { "processes/task-approval-process.bpmn20.xml" })
    void reviewRejectedFlow() {

        Long recordId = 8977L;

        workflowController.updateRecordStatus(recordId,null);

        var response = workflowController.getRecordState(recordId);

        assertEquals(String.valueOf("INIT"), String.valueOf(response.getBody().get("workflowState")));

        WorkflowDTO workflowDTO = WorkflowDTO.builder()
            .workflowState(WorkflowDTO.WorkflowState.DOCUMENT_READY_FOR_REVIEW)
            .state(WorkflowDTO.State.DRAFTED)
            .build();

        workflowController.updateRecordStatus(recordId,workflowDTO);

        response = workflowController.getRecordState(recordId);

        assertEquals(String.valueOf("DOCUMENT_READY_FOR_REVIEW"), String.valueOf(response.getBody().get("workflowState")));
        assertEquals(String.valueOf("DRAFTED"), String.valueOf(response.getBody().get("state")));

        workflowDTO = WorkflowDTO.builder()
            .workflowState(WorkflowDTO.WorkflowState.REVIEW_REJECTED)
            .state(WorkflowDTO.State.DRAFTED)
            .build();

        workflowController.updateRecordStatus(recordId,workflowDTO);

        response = workflowController.getRecordState(recordId);

        assertEquals(String.valueOf("REVIEW_REJECTED"), String.valueOf(response.getBody().get("workflowState")));
        assertEquals(String.valueOf("DRAFTED"), String.valueOf(response.getBody().get("state")));

    }

    @Test
    @Deployment(resources = { "processes/task-approval-process.bpmn20.xml" })
    void approvalRejectedFlow() {

        Long recordId = 8976L;

        workflowController.updateRecordStatus(recordId,null);

        var response = workflowController.getRecordState(recordId);

        assertEquals(String.valueOf("INIT"), String.valueOf(response.getBody().get("workflowState")));

        WorkflowDTO workflowDTO = WorkflowDTO.builder()
            .workflowState(WorkflowDTO.WorkflowState.DOCUMENT_READY_FOR_REVIEW)
            .state(WorkflowDTO.State.DRAFTED)
            .build();

        workflowController.updateRecordStatus(recordId,workflowDTO);

        response = workflowController.getRecordState(recordId);

        assertEquals(String.valueOf("DOCUMENT_READY_FOR_REVIEW"), String.valueOf(response.getBody().get("workflowState")));
        assertEquals(String.valueOf("DRAFTED"), String.valueOf(response.getBody().get("state")));

        workflowDTO = WorkflowDTO.builder()
            .workflowState(WorkflowDTO.WorkflowState.REVIEW_ACCEPTED)
            .state(WorkflowDTO.State.DRAFTED)
            .build();

        workflowController.updateRecordStatus(recordId,workflowDTO);

        response = workflowController.getRecordState(recordId);

        assertEquals(String.valueOf("REVIEW_ACCEPTED"), String.valueOf(response.getBody().get("workflowState")));
        assertEquals(String.valueOf("REVIEWED"), String.valueOf(response.getBody().get("state")));


        workflowDTO = WorkflowDTO.builder()
            .workflowState(WorkflowDTO.WorkflowState.APPROVAL_REJECTED)
            .state(WorkflowDTO.State.REVIEWED)
            .build();

        workflowController.updateRecordStatus(recordId,workflowDTO);

        response = workflowController.getRecordState(recordId);

        assertEquals(String.valueOf("APPROVAL_REJECTED"), String.valueOf(response.getBody().get("workflowState")));
        assertEquals(String.valueOf("DRAFTED"), String.valueOf(response.getBody().get("state")));

    }

    @Test
    @Deployment(resources = { "processes/task-approval-process.bpmn20.xml" })
    void duplicateProcessWithSameRecordId() {

        Long recordId = 9000L;

        workflowController.updateRecordStatus(recordId,null);

        var response = workflowController.getRecordState(recordId);

        ProcessingException exception = assertThrows(ProcessingException.class, () -> {
            // Call your method that should throw the exception
            workflowController.updateRecordStatus(recordId,null);
        });

        // Verify the exception message
        assertEquals("Unexpected error processing record ID: 9000. Please contact system administrator.",
            exception.getMessage());

    }

    @Test
    @Deployment(resources = { "processes/task-approval-process.bpmn20.xml" })
    void recordNotFountWithProcessId() {

        Long recordId = 9000L;

        workflowController.updateRecordStatus(recordId,null);

        workflowController.getRecordState(recordId);

        RecordNotFoundException exception = assertThrows(RecordNotFoundException.class, () -> {

            Long unknownRecordId = 7000L;

            // Call your method that should throw the exception
            WorkflowDTO workflowDTO = WorkflowDTO.builder()
                .workflowState(WorkflowDTO.WorkflowState.DOCUMENT_READY_FOR_REVIEW)
                .state(WorkflowDTO.State.DRAFTED)
                .build();

            workflowController.updateRecordStatus(unknownRecordId,workflowDTO);
        });

        // Verify the exception message
        assertEquals("Record not found: 7000",
            exception.getMessage());

    }

    @Test
    @Deployment(resources = { "processes/task-approval-process.bpmn20.xml" })
    void invalidStatus() {

        Long recordId = 9000L;

        workflowController.updateRecordStatus(recordId,null);

        workflowController.getRecordState(recordId);

        InvalidStatusException exception = assertThrows(InvalidStatusException.class, () -> {

            // Call your method that should throw the exception
            WorkflowDTO workflowDTO = WorkflowDTO.builder()
                .workflowState(WorkflowDTO.WorkflowState.DOCUMENT_READY_FOR_REVIEW)
                .state(WorkflowDTO.State.SIGNED)
                .build();

            workflowController.updateRecordStatus(recordId,workflowDTO);
        });

        // Verify the exception message
        assertEquals("Invalid state: Please provide a valid state",
            exception.getMessage());

    }

}