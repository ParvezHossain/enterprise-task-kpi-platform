package com.parvez.task.web;

import java.net.URI;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import com.parvez.task.service.InvalidTaskReferenceException;
import com.parvez.task.service.TaskNotFoundException;
import com.parvez.task.service.TaskVersionConflictException;
import com.parvez.task.domain.InvalidTaskTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.OptimisticLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TaskApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> errors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(error -> error.getField(), error -> error.getDefaultMessage(),
                        (first, ignored) -> first, TreeMap::new));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "One or more request fields are invalid.", "urn:task-management:problem:validation-error", request);
        problem.setProperty("errors", errors);
        return response(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadableRequest(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return response(problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "The request body is malformed or contains an unsupported value.",
                "urn:task-management:problem:invalid-request", request));
    }

    @ExceptionHandler(InvalidTaskReferenceException.class)
    ResponseEntity<ProblemDetail> invalidReference(InvalidTaskReferenceException exception,
            HttpServletRequest request) {
        return response(problem(HttpStatus.BAD_REQUEST, "Invalid task reference", exception.getMessage(),
                "urn:task-management:problem:invalid-task-reference", request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(AccessDeniedException exception, HttpServletRequest request) {
        return response(problem(HttpStatus.FORBIDDEN, "Forbidden", "The authenticated user cannot create this task.",
                "urn:task-management:problem:forbidden", request));
    }

    @ExceptionHandler({TaskVersionConflictException.class, InvalidTaskTransitionException.class,
            ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<ProblemDetail> conflict(RuntimeException exception, HttpServletRequest request) {
        return response(problem(HttpStatus.CONFLICT, "Task conflict", exception.getMessage(),
                "urn:task-management:problem:conflict", request));
    }

    @ExceptionHandler(TaskNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(TaskNotFoundException exception, HttpServletRequest request) {
        return response(problem(HttpStatus.NOT_FOUND, "Task not found", "The requested task does not exist.",
                "urn:task-management:problem:not-found", request));
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String type,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(type));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    private ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .headers(headers -> headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON))
                .body(problem);
    }
}