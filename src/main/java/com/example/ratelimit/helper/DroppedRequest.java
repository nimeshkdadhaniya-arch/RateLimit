package com.example.ratelimit.helper;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;

@Getter
public class DroppedRequest {
    private final String type;
    private final String key;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private int attempts;
    AsyncContext asyncContext;
    FilterChain filterChain;

    public DroppedRequest(HttpServletRequest request, HttpServletResponse response,FilterChain filterChain,AsyncContext asyncContext) {
        this.type = request.getParameter("type");
        this.key = request.getParameter("key");
        this.asyncContext=asyncContext;
        this.filterChain =filterChain;
        this.request = request;
        this.response = response;
        this.attempts = 0;
    }


    public void incrementAttempts() { this.attempts++; }
}
