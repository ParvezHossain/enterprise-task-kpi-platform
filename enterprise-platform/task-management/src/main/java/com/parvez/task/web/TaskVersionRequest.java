package com.parvez.task.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TaskVersionRequest(@NotNull @PositiveOrZero Long version) {
}