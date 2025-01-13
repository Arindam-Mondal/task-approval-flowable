package com.task.flowable.taskapprovalflowable.exception;

public class ProcessingException extends RuntimeException {
    public ProcessingException(String message) {
        super(message);
    }
    public ProcessingException(String message, Throwable e) {
        super(message, e);
    }
}
