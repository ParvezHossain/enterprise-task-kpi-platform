package com.parvez.auth.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Public errors never include exception messages, rejected values, or stack traces. */
public final class ProblemResponses {
    private ProblemResponses() { }

    public static ProblemDetail create(int status, String path) {
        HttpStatus known = HttpStatus.resolve(status);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), switch (status) {
            case 400 -> "The request is invalid.";
            case 401 -> "Authentication failed or is required.";
            case 403 -> "Access to this resource is denied.";
            case 404 -> "The requested resource was not found.";
            case 405 -> "The HTTP method is not supported.";
            case 409 -> "The request conflicts with the current resource state.";
            case 415 -> "The request content type is not supported.";
            case 429 -> "Too many requests. Try again later.";
            default -> status >= 500 ? "An unexpected server error occurred." : "The request could not be completed.";
        });
        problem.setType(URI.create("about:blank"));
        problem.setTitle(known == null ? "HTTP error" : known.getReasonPhrase());
        // Exclude the query: OAuth codes, state, and other credentials can occur there.
        problem.setInstance(URI.create(path));
        return problem;
    }
}
