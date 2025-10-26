package com.example.RateLimit.Helper;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
@Configuration
@ConfigurationProperties(prefix = "ratelimit.queue.retry")
@Data
public class RateLimitQueueConfig {


        private int queueSize;
        private long intervalMs;

}
