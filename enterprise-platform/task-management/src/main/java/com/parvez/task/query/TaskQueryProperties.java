package com.parvez.task.query;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("task.query")
public record TaskQueryProperties(@Min(1) int maximumPageSize) {
}
