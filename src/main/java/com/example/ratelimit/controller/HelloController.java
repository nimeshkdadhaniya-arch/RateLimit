package com.example.ratelimit.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/TokenBucketTest")
    public String home() {
        return "TokenBucket Request accepted!";
    }
}