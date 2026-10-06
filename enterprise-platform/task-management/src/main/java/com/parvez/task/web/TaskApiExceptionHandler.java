package com.parvez.task.web;

import java.net.URI;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.parvez.task.service.InvalidTaskReferenceException;
import com.parvez.task.service.InvalidTaskQueryException;
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
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    @ExceptionHandler({InvalidTaskQueryException.class, BindException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> invalidQuery(Exception exception, HttpServletRequest request) {
        return response(problem(HttpStatus.BAD_REQUEST, "Invalid query", "The query parameters are invalid.",
                "urn:task-management:problem:invalid-query", request));
    }

    @ExceptionHandler(InvalidTaskReferenceException.class)
    ResponseEntity<ProblemDetail> invalidReference(InvalidTaskReferenceException exception,
                                                   HttpServletRequest request) {
        return response(problem(HttpStatus.BAD_REQUEST, "Invalid task reference", exception.getMessage(),
                "urn:task-management:problem:invalid-task-reference", request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> forbidden(AccessDeniedException exception, HttpServletRequest request) {
        return response(problem(HttpStatus.FORBIDDEN, "Forbidden", "The authenticated user cannot perform this operation.",
                "urn:task-management:problem:forbidden", request));
    }

    @ExceptionHandler({TaskVersionConflictException.class, InvalidTaskTransitionException.class,
            ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<ProblemDetail> conflict(RuntimeException exception, HttpServletRequest request) {
        return response(problem(HttpStatus.CONFLICT, "Task conflict",
                exception instanceof TaskVersionConflictException || exception instanceof InvalidTaskTransitionException
                        ? exception.getMessage() : "The task changed while the request was being processed.",
                "urn:task-management:problem:conflict", request));
    }

    @ExceptionHandler(TaskNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(TaskNotFoundException exception, HttpServletRequest request) {
        return response(problem(HttpStatus.NOT_FOUND, "Task not found", "The requested task does not exist.",
                "urn:task-management:problem:not-found", request));
    }

    @ExceptionHandler(com.parvez.task.service.IdempotencyException.class)
    ResponseEntity<ProblemDetail> idempotency(com.parvez.task.service.IdempotencyException exception, HttpServletRequest request) {
        return response(problem(exception.conflict() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST, "Idempotency error", exception.getMessage(), "urn:task-management:problem:idempotency", request));
    }

    @ExceptionHandler(org.springframework.dao.CannotAcquireLockException.class)
    ResponseEntity<ProblemDetail> busy(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(409).header("Retry-After", "1").body(problem(HttpStatus.CONFLICT, "Command busy", "Retry with the same idempotency key.", "urn:task-management:problem:busy", request));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(Exception exception, HttpServletRequest request) {
        return response(problem(HttpStatus.CONFLICT, "Task conflict", "The operation conflicts with the current task data.", "urn:task-management:problem:conflict", request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
        HttpStatus status = exception instanceof org.springframework.web.ErrorResponse framework
                ? HttpStatus.valueOf(framework.getStatusCode().value()) : HttpStatus.INTERNAL_SERVER_ERROR;
        return response(problem(status, status.getReasonPhrase(), "The request could not be processed.", "urn:task-management:problem:request-error", request));
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