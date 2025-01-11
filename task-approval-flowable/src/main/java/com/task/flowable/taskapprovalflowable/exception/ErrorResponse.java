package com.task.flowable.taskapprovalflowable.exception;

import lombok.Getter;

@Getter
public class ErrorResponse {
    // Getters and Setters
    private int statusCode;
    private String message;

    // Constructor
    public ErrorResponse(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}
