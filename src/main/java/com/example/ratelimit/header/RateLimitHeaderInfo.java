package com.example.ratelimit.header;

import lombok.Data;

@Data
public class RateLimitHeaderInfo {

    private final long maxLimit;
    private final long remainingLimit;
    private final long usedLimit;
    private final long nextWindowTime;
}
