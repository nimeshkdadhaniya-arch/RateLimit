package com.example.ratelimit.service;

import com.example.ratelimit.api.GlobalRateLimiter;
import com.example.ratelimit.api.IRateLimitService;
import com.example.ratelimit.api.KeyRateLimiter;
import com.example.ratelimit.api.RateLimiter;
import com.example.ratelimit.helper.RateLimiterTypeEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class RateLimitService implements IRateLimitService {

    @Autowired
    private RateLimitFilterFactoryConfig rateLimitFactoryConfig;


    public boolean tryAcquire(HttpServletRequest request, HttpServletResponse response) {
        String type = request.getParameter("type");
        String keyVal = request.getParameter("key");
        RateLimiterTypeEnum typeEnum;
        boolean isAcquired = false;
        typeEnum = RateLimiterTypeEnum.valueOf(type.trim().toUpperCase());

        if (keyVal != null) {
            KeyRateLimiter rateLimiterKey = rateLimitFactoryConfig.filterBasedOnKey(typeEnum).get();
            isAcquired = rateLimiterKey.tryAcquire(keyVal);
            setHeaders(request,response,rateLimiterKey);
        } else {
            GlobalRateLimiter limiter = rateLimitFactoryConfig.filterBasedOnType(typeEnum);
            isAcquired = limiter.tryAcquire();
            setHeaders(request,response,limiter);
        }
        return isAcquired;
    }


    public void setHeaders(HttpServletRequest request,HttpServletResponse response, RateLimiter rateLimiter)
    {
        response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimiter.getRateLimitHeaderInfo(request).getMaxLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(rateLimiter.getRateLimitHeaderInfo(request).getRemainingLimit()));
        response.setHeader("X-RateLimit-Used", String.valueOf(rateLimiter.getRateLimitHeaderInfo(request).getUsedLimit()));
        LocalDateTime dateTimeMs = LocalDateTime.ofInstant(Instant.ofEpochMilli(rateLimiter.getRateLimitHeaderInfo(request).getNextWindowTime()), ZoneId.systemDefault());
        response.setHeader("X-RateLimit-Reset", dateTimeMs.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

    }

}