package com.parvez.task.query;

import java.util.List;
import com.parvez.task.web.TaskResponse;

public record TaskPageResponse(
        List<TaskResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {
}
