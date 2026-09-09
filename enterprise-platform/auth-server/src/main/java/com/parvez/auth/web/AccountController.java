package com.parvez.auth.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal authenticated landing page; credentials and identity records never enter the view. */
@RestController
public class AccountController {
    @GetMapping(value = "/account", produces = MediaType.TEXT_HTML_VALUE)
    public String account() {
        return """
                <!doctype html>
                <html lang="en">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Signed in</title></head>
                <body><main><h1>You are signed in</h1>
                <p>Return to your application to continue.</p>
                <a href="logout">Sign out</a></main></body>
                </html>
                """;
    }
}
