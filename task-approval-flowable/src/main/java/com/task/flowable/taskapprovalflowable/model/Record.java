package com.task.flowable.taskapprovalflowable.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Record {
    @Id
    private Long id;

    private String title;
    private String description;
    private String processInstanceId;

    @Enumerated(EnumType.STRING)
    private RecordState state = RecordState.DRAFT;

    private String createdBy;

    @CreatedDate
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime lastModifiedAt;

    @Column(length = 1000)
    private String comments;
}