package com.parvez.task.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

public interface TaskSearchRepository {
    Page<TaskEntity> search(Specification<TaskEntity> specification, Pageable pageable);
}