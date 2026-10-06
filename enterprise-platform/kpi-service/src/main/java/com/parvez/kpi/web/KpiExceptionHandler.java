package com.parvez.kpi.web;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class KpiExceptionHandler {
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handle(Exception error, HttpServletRequest request) {
        int status = error instanceof org.springframework.security.access.AccessDeniedException ? 403 :
                error instanceof IllegalArgumentException || error instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ? 400 :
                        error instanceof org.springframework.web.ErrorResponse framework ? framework.getStatusCode().value() :
                                error instanceof com.parvez.kpi.service.KpiUnavailableException || error instanceof org.springframework.web.client.RestClientException ? 503 : 500;
        String title = HttpStatus.valueOf(status).getReasonPhrase();
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), status == 503 ? "KPI data or authorization service is temporarily unavailable." : "The request could not be processed.");
        problem.setTitle(title);
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
