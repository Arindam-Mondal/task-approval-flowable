package com.task.flowable.taskapprovalflowable.repository;

import com.task.flowable.taskapprovalflowable.model.Record;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecordRepository extends JpaRepository<Record, Long> {
}
