package com.example.ratelimit.service;

import com.example.ratelimit.api.GlobalRateLimiter;
import com.example.ratelimit.api.KeyRateLimiter;
import com.example.ratelimit.helper.RateLimiterTypeEnum;
import com.example.ratelimit.helper.TokenBucketConfig;
import com.example.ratelimit.impl.FixedWindowRateLimit;
import com.example.ratelimit.impl.SlidingWindowLogRateLimit;
import com.example.ratelimit.impl.SlidingWindowLogUserRateLimit;
import com.example.ratelimit.impl.TokenBucketRateLimit;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class RateLimitFilterFactoryConfig {

    @Getter
    @Value("${token.bucket.bucketCapacity}")
    long bucketCapacity;
    @Getter
    @Value("${token.bucket.refillTokens}")
    long refillTokens;
    @Getter
    @Value("${token.bucket.refillIntervalMillis}")
    long refillIntervalMillis;

    @Getter
    @Value("${fix.window.counter.limit}")
    int max_requests_per_window;

    @Getter
    @Value("${fix.window.counter.timewindowMillis}")
    long window_size_ms;

    @Getter
    @Value("${sliding.log.limit}")
    int slidinglogwindowSize;

    @Getter
    @Value("${sliding.log.timewindowMillis}")
    long slidinglogwindowTimeMillis;
    @Getter
    @Value("${sliding.log.user.limit}")
    int slidingloguserwindowSize;

    @Getter
    @Value("${sliding.log.user.timewindowMillis}")
    long slidingloguserwindowsTimeMillis;


    public Optional<KeyRateLimiter> filterBasedOnKey(RateLimiterTypeEnum type)
    {
        if(type.equals(RateLimiterTypeEnum.SLIDING_WINDOW_LOG_USER))
        {
            KeyRateLimiter slidingWindowLogUserRateLimit = getSlidingWindowLogUserRateLimit();
            return  Optional.of(slidingWindowLogUserRateLimit);
        }
        return  Optional.empty();
    }


    public GlobalRateLimiter filterBasedOnType(RateLimiterTypeEnum type)
    {
        return switch (type) {
            case TOKEN_BUCKET -> getTokenBucket();
            case FIXED_WINDOW -> getFixedWindow();
            case SLIDING_WINDOW_LOG -> getSlidingWindowLog();
            default -> getTokenBucket();
        };

    }

    @Bean
    public GlobalRateLimiter getTokenBucket()
    {
        // Create a single global token bucket
        TokenBucketConfig tokenBucketConfig = new TokenBucketConfig(bucketCapacity, refillTokens, refillIntervalMillis);
        GlobalRateLimiter tokenBucket = new TokenBucketRateLimit(tokenBucketConfig);
        return tokenBucket;
    }
    @Bean
    public GlobalRateLimiter getFixedWindow()
    {
        // Create a single global token bucket
        GlobalRateLimiter fixedWindow = new FixedWindowRateLimit(max_requests_per_window,  window_size_ms);
        return fixedWindow;
    }

    @Bean
    public GlobalRateLimiter getSlidingWindowLog()
    {
        // Create a single global token bucket
        GlobalRateLimiter slidingWindowLog = new SlidingWindowLogRateLimit(slidinglogwindowSize,  slidinglogwindowTimeMillis);
        return slidingWindowLog;
    }

    @Bean
    public KeyRateLimiter getSlidingWindowLogUserRateLimit()
    {
        // Create a single global token bucket
        KeyRateLimiter slidingWindowLogUserRateLimit = new SlidingWindowLogUserRateLimit(slidingloguserwindowSize,  slidingloguserwindowsTimeMillis,System::currentTimeMillis);
        return slidingWindowLogUserRateLimit;
    }
}
