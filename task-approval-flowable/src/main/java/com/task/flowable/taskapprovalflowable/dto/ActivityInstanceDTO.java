package com.task.flowable.taskapprovalflowable.dto;

import lombok.Data;

import java.util.Date;

@Data
public class ActivityInstanceDTO {
    private String activityId;
    private String activityName;
    private String activityType;
    private Date startTime;
    private Date endTime;
    private String state;

    public ActivityInstanceDTO(String activityId, String activityName, String activityType,
                               Date startTime, Date endTime, String state) {
        this.activityId = activityId;
        this.activityName = activityName;
        this.activityType = activityType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.state = state;
    }
}
