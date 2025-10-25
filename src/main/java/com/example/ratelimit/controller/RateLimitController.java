package com.example.ratelimit.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RateLimitController {

    @GetMapping("/limit")
    public ResponseEntity checkLimit() {

        String message= "Limit OK";
        return ResponseEntity
                .ok() // HTTP 200 OK
                .body(message);

    }
}