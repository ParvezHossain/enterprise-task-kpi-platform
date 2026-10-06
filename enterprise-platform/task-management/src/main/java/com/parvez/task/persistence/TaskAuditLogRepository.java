package com.parvez.task.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface TaskAuditLogRepository extends Repository<TaskAuditLogEntity, UUID> {
    Page<TaskAuditLogEntity> findByTaskIdOrderByOccurredAtDesc(UUID taskId, Pageable pageable);
}