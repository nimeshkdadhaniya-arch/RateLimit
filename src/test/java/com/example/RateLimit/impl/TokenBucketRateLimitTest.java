package com.example.RateLimit.impl;


import com.example.ratelimit.helper.TokenBucketConfig;
import com.example.ratelimit.impl.TokenBucketRateLimit;
import com.example.RateLimit.RateLimitAplicationTests;
import lombok.Getter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.junit.jupiter.api.BeforeEach;
import com.example.ratelimit.api.GlobalRateLimiter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class TokenBucketRateLimitTest extends RateLimitAplicationTests {

    TokenBucketConfig tokenBucketConfig;

    @Getter
    @Value("${token.bucket.bucketCapacity}")
    long bucketCapacity;
    @Getter
    @Value("${token.bucket.refillTokens}")
    long refillTokens;
    @Getter
    @Value("${token.bucket.refillIntervalMillis}")
    long refillIntervalMillis;

    @BeforeEach
    void setup() {
        // Create a single global token bucket
        tokenBucketConfig = new TokenBucketConfig(bucketCapacity, refillTokens, refillIntervalMillis);
    }

    @Test //Allow Requests Within Limit
    void testAllowRequestsUnderLimit() {// 2 requests per second
        GlobalRateLimiter rateLimiter = new TokenBucketRateLimit(tokenBucketConfig);
        for (int i = 0; i < refillTokens; i++) {
            Assertions.assertEquals(true,rateLimiter.tryAcquire());
        }
    }

    @Test //Block Requests Exceeding Limit
    void testBlockRequestsExceedingLimit() {
        GlobalRateLimiter rateLimiter = new TokenBucketRateLimit(tokenBucketConfig); // 2 requests per second
        for (int i = 0; i < refillTokens; i++) {
            rateLimiter.tryAcquire();
        }
        // 3rd request should fail
        Assertions.assertFalse(rateLimiter.tryAcquire());
    }

    @Test //After Reset/Refill test
    void testWindowReset() throws InterruptedException {
        GlobalRateLimiter rateLimiter = new TokenBucketRateLimit(tokenBucketConfig);// 2 requests per 500ms

        Assertions.assertTrue(rateLimiter.tryAcquire());
        Assertions.assertTrue(rateLimiter.tryAcquire());

        Thread.sleep(refillIntervalMillis); // wait for window to reset

        Assertions.assertTrue(rateLimiter.tryAcquire()); // should be allowed again
    }

    @Test //Burst test upto bucket capacity (Should not accumulate excess tokens beyond capacity)
    void testTokenBucketBurst() throws InterruptedException {
        TokenBucketConfig tokenBucketConfig1 = new TokenBucketConfig(4, refillTokens, refillIntervalMillis);
        GlobalRateLimiter rateLimiter = new TokenBucketRateLimit(tokenBucketConfig1); // capacity 4

        Thread.sleep(refillIntervalMillis);
        //Thread.sleep(60000);
        // Burst: consume all tokens
        for (int i = 0; i < 4; i++) {
            Assertions.assertTrue(rateLimiter.tryAcquire());
        }
        // No tokens left
        Assertions.assertFalse(rateLimiter.tryAcquire());
    }


    @Test
    void testConcurrentRequests() throws InterruptedException, ExecutionException {
        GlobalRateLimiter rateLimiter = new TokenBucketRateLimit(tokenBucketConfig);
        int allowedCount = 0;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            futures.add(executor.submit(() -> rateLimiter.tryAcquire()));
        }

        for (Future<Boolean> f : futures) {
            if (f.get()) allowedCount++;
        }

        // Only 2 should be allowed as per bucket capacity
        Assertions.assertEquals(2, allowedCount);
        executor.shutdown();
    }

    @Test
    void testZeroCapacityLimiter() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new TokenBucketRateLimit(new TokenBucketConfig(0, 2,60000));
        });
    }


    @Test //Block Requests Exceeding Limit
    void testHighLimit() {
        TokenBucketConfig tokenBucketConfig1 = new TokenBucketConfig(Integer.MAX_VALUE, Integer.MAX_VALUE, refillIntervalMillis);
        GlobalRateLimiter rateLimiter = new TokenBucketRateLimit(tokenBucketConfig1); // 2 requests per second
        for (int i = 0; i < 2; i++) {
            rateLimiter.tryAcquire();
        }
        // 3rd request should allow too
        Assertions.assertTrue(rateLimiter.tryAcquire());
    }


}
