package com.parvez.task.query;

public enum TaskSortField {
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt"),
    DUE_DATE("dueDate"),
    TITLE("title"),
    STATUS("status"),
    PRIORITY("priority");

    private final String property;

    TaskSortField(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}
