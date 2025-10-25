package com.example.RateLimit.impl;

import com.example.RateLimit.RateLimitAplicationTests;
import com.example.ratelimit.api.GlobalRateLimiter;
import com.example.ratelimit.impl.SlidingWindowLogRateLimit;
import lombok.Getter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SlidingWindowLogRateLimitTest  extends RateLimitAplicationTests {

    private GlobalRateLimiter rateLimiter;
    @Getter
    @Value("${sliding.log.windowsize}")
    int maxRequestsPerWindow;
    @Getter
    @Value("${sliding.log.timewindowMillis}")
    long windowInMillis;

    @BeforeEach
    void setup() {
        // 5 requests per second per user
         rateLimiter = new SlidingWindowLogRateLimit(maxRequestsPerWindow, windowInMillis);
    }


    @Test
    void testAllowRequestsUnderLimit() {// 2 requests per second
        for (int i = 0; i < 2; i++) {
            Assertions.assertEquals(true,rateLimiter.tryAcquire());
        }
    }

    @Test
    void testBlockRequestsExceedingLimit() {
        for (int i = 0; i < 2; i++) {
            rateLimiter.tryAcquire();
        }
        // 3rd request should fail
        Assertions.assertFalse(rateLimiter.tryAcquire());
    }


    @Test
    void testWindowSlideAllowsRequestsAgain() throws InterruptedException {

        for (int i = 0; i < 2; i++) {
            Assertions.assertTrue(rateLimiter.tryAcquire());
        }
        // Denied before window reset
        Assertions.assertFalse(rateLimiter.tryAcquire());

        // Wait for full window (1 second)
        Thread.sleep(windowInMillis);

        // Should be allowed again
        Assertions.assertTrue(rateLimiter.tryAcquire());
    }

    @Test
    void testThreadSafetyUnderConcurrency() throws InterruptedException {
        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            pool.execute(() -> {
                if (rateLimiter.tryAcquire()) {
                    allowedCount.incrementAndGet();
                }
                latch.countDown();
            });
        }

        latch.await();
        pool.shutdown();

        Assertions.assertTrue(allowedCount.get() <= 2,
                "Allowed requests should not exceed limit; got: " + allowedCount.get());
    }

}
