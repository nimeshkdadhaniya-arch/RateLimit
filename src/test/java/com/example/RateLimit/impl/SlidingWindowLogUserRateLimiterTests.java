package com.example.RateLimit.impl;

import com.example.RateLimit.RateLimitAplicationTests;
import com.example.ratelimit.api.KeyRateLimiter;
import com.example.ratelimit.helper.TokenBucketConfig;
import com.example.ratelimit.impl.SlidingWindowLogUserRateLimit;
import com.example.ratelimit.impl.TokenBucketRateLimit;
import lombok.Getter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SlidingWindowLogUserRateLimiterTests  extends RateLimitAplicationTests {

    private KeyRateLimiter rateLimiter;
    @Getter
    @Value("${sliding.log.user.limit}")
    int maxRequestsPerWindow;
    @Getter
    @Value("${sliding.log.user.timewindowMillis}")
    long windowInMillis;

    @BeforeEach
    void setup() {
        // 5 requests per second per user
        rateLimiter = new SlidingWindowLogUserRateLimit(maxRequestsPerWindow, windowInMillis,System::currentTimeMillis);
    }

    @Test //Allow Requests Within Limit
    void testAllowRequestsUnderLimit() {// 2 requests per second
        String user = "user1";
        for (int i = 0; i < maxRequestsPerWindow; i++) {
            Assertions.assertEquals(true,rateLimiter.tryAcquire(user));
        }
    }


    @Test //Block Requests Exceeding Limit
    void testBlockRequestsExceedingLimit() {
        String user = "user1";
        for (int i = 0; i < maxRequestsPerWindow; i++) {
            Assertions.assertEquals(true,rateLimiter.tryAcquire(user));
        }
        // 3rd request should fail
        Assertions.assertFalse(rateLimiter.tryAcquire(user));
    }


   @Test //After window slide, requests are allowed again
    void testWindowSlideAllowsRequestsAgain() throws InterruptedException {
        String user = "user2";

        for (int i = 0; i < maxRequestsPerWindow; i++) {
            Assertions.assertTrue(rateLimiter.tryAcquire(user));
        }

        // Denied before window reset
        Assertions.assertFalse(rateLimiter.tryAcquire(user));

        // Wait for full window (1 second)
        Thread.sleep(windowInMillis);

        // Should be allowed again
        Assertions.assertTrue(rateLimiter.tryAcquire(user));
    }

    @Test //multiple users independence
    void testPerUserIndependence() {
        String userA = "Alice";
        String userB = "Bob";

        // Alice uses up her quota
        for (int i = 0; i < maxRequestsPerWindow; i++) {
            Assertions.assertTrue(rateLimiter.tryAcquire(userA));
        }
        // Denied before window reset
        Assertions.assertFalse(rateLimiter.tryAcquire(userA));

        // Bob still has quota
        for (int i = 0; i < maxRequestsPerWindow; i++) {
            Assertions.assertTrue(rateLimiter.tryAcquire(userB));
        }
    }

    // Thread safety test: concurrent access for same user
    @Test
    void testThreadSafetyUnderConcurrency() throws InterruptedException {
        String user = "concurrentUser";
        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            pool.execute(() -> {
                if (rateLimiter.tryAcquire(user)) {
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

    @Test
    void testZeroCapacityLimiter() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new SlidingWindowLogUserRateLimit(0, 0,System::currentTimeMillis);
        });
    }
}
