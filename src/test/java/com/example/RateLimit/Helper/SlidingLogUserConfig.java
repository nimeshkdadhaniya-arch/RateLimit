package com.example.RateLimit.Helper;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "sliding.log.user")
@Data
public class SlidingLogUserConfig {
    private int limit;
    private long timeWindowMillis;
}