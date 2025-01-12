package com.task.flowable.taskapprovalflowable.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProcessInstanceDTO {
    private String processId;
    private String instanceId;
    private String state;

    public ProcessInstanceDTO(String processId, String instanceId, String state) {
        this.processId = processId;
        this.instanceId = instanceId;
        this.state = state;
    }
}
