package com.parvez.task.persistence;

import com.parvez.task.domain.TaskStatus;
import com.parvez.task.domain.TaskPriority;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "tasks")
public class TaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;

    @Column(nullable = false, length = 16)
    private String priority;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected TaskEntity() {
    }

    public static TaskEntity draft(String title, String description, TaskPriority priority,
                                   UUID teamId, UUID projectId, UUID createdBy, LocalDate dueDate) {
        TaskEntity task = new TaskEntity();
        task.title = title;
        task.description = description;
        task.status = TaskStatus.DRAFT;
        task.priority = priority.name();
        task.teamId = teamId;
        task.projectId = projectId;
        task.createdBy = createdBy;
        task.dueDate = dueDate;
        task.createdAt = Instant.now();
        task.updatedAt = task.createdAt;
        return task;
    }

    public void updateStatus(TaskStatus status, Instant updatedAt) {
        this.status = status;
        if (status == TaskStatus.IN_PROGRESS) this.startedAt = updatedAt;
        if (status == TaskStatus.COMPLETED) this.completedAt = updatedAt;
        if (status == TaskStatus.CLOSED) this.closedAt = updatedAt;
        this.updatedAt = updatedAt;
    }

    public void assignTo(UUID assignedTo, Instant updatedAt) {
        this.assignedTo = assignedTo;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getPriority() {
        return priority;
    }

    public UUID getAssignedTo() {
        return assignedTo;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}