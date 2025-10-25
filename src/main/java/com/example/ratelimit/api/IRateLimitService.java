package com.example.ratelimit.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface IRateLimitService {
    boolean tryAcquire(HttpServletRequest request, HttpServletResponse response);
    void setHeaders(HttpServletRequest request,HttpServletResponse response, RateLimiter rateLimiter);
}
