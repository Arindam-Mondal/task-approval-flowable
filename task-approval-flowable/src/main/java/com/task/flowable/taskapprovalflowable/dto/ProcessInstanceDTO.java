package com.task.flowable.taskapprovalflowable.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProcessInstanceDTO {
    private String processId;
    private String instanceId;
    private String state;
    private List<ActivityInstanceDTO> activities;

    public ProcessInstanceDTO(String processId, String instanceId, String state, List<ActivityInstanceDTO> activities) {
        this.processId = processId;
        this.instanceId = instanceId;
        this.state = state;
        this.activities = activities;
    }
}
