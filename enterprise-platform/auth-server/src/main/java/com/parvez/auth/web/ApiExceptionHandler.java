package com.parvez.auth.web;

import com.parvez.auth.service.RegistrationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return new ResponseEntity<>(ProblemResponses.create(status.value(),
                ((ServletWebRequest) request).getRequest().getRequestURI()), headers, status);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> validation(Exception exception, WebRequest request) {
        return handleExceptionInternal(exception, null, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> authentication(Exception exception, WebRequest request) {
        return handleExceptionInternal(exception, null, new HttpHeaders(), HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<Object> denied(Exception exception, WebRequest request) {
        return handleExceptionInternal(exception, null, new HttpHeaders(), HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(RegistrationException.class)
    ResponseEntity<Object> registration(RegistrationException exception, WebRequest request) {
        HttpStatus status = switch (exception.getReason()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case EMAIL_UNAVAILABLE -> HttpStatus.CONFLICT;
            case ROLE_UNAVAILABLE, PERSISTENCE_FAILURE -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return handleExceptionInternal(exception, null, new HttpHeaders(), status, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception exception, WebRequest request) {
        return handleExceptionInternal(exception, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }
}
