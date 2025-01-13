package com.task.flowable.taskapprovalflowable.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class WorkflowDTO {

    public enum WorkflowState {
        INIT,
        DOCUMENT_READY_FOR_REVIEW,
        REVIEW_REJECTED,
        REVIEW_ACCEPTED,
        APPROVAL_REJECTED,
        APPROVAL_ACCEPTED
    }

    public enum State {
        DRAFTED,
        SIGNED,
        REVIEWED
    }

    private WorkflowState workflowState;
    private String recordType;
    private String standard;
    private State state;
}
