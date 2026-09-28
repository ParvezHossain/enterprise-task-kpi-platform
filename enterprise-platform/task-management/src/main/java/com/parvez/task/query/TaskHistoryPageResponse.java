package com.parvez.task.query;

import java.util.List;

public record TaskHistoryPageResponse(
        List<TaskHistoryEntry> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {
}
