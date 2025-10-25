package com.example.ratelimit.impl;

import com.example.ratelimit.helper.TokenBucketConfig;
import com.example.ratelimit.api.GlobalRateLimiter;
import com.example.ratelimit.header.RateLimitHeaderInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class TokenBucketRateLimit implements GlobalRateLimiter {

    //maxBucket_size
    //No. of refill token
    //refill timestamp
    //availableToken
    //lastRefilltimestamp

    private final long bucket_capacity; //2 request (token)
    private final long refillRate; //2 refill token
    private final long refillIntervalMillis; //5 second
    private final AtomicLong availableTokens;
    private final AtomicLong lastRefillTimestamp;

    public TokenBucketRateLimit(TokenBucketConfig tokenBucketConfig) {
        this.bucket_capacity = tokenBucketConfig.getBucket_capacity();
        this.refillRate = tokenBucketConfig.getRefillTokens();
        this.refillIntervalMillis = tokenBucketConfig.getRefillIntervalMillis();
        this.availableTokens = new AtomicLong(refillRate);
        this.lastRefillTimestamp = new AtomicLong(System.currentTimeMillis());
    }

    @Override
    public boolean tryAcquire() {
        refill();
        long currentTokens;


       /* if(getAvailableTokens()>0) {
            availableTokens.decrementAndGet();
            return true;
        }
        return false;*/
/*
        do {
            currentTokens = availableTokens.get();
            if (currentTokens == 0) {
                return false; // Bucket empty
            }
        } while (!availableTokens.compareAndSet(currentTokens, currentTokens - 1));*/

        while (true) {
            currentTokens = availableTokens.get();
            log.debug("TokenBucketRateLimit currentTokens={}", currentTokens);
            if (currentTokens <= 0) {
                return false; // no tokens left
            }
            // Try to atomically decrement
            if (availableTokens.compareAndSet(currentTokens, currentTokens - 1)) {
                return true; // success
            }
            // else retry because another thread changed the value
        }

        //return true;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long lastRefill;

        //after refillTime,add token
       /* if (elapsed > refillIntervalMillis) {
            long tokensToAdd =(elapsed/refillIntervalMillis) * refillRate; //in stead scheduler which add one token every minutes.
            long newTokenCount = Math.min(bucket_capacity, availableTokens.get() + tokensToAdd);
            availableTokens.set(newTokenCount);
            lastRefillTimestamp = now;
        }*/

        while (true) {
            lastRefill =lastRefillTimestamp.get();//5 sec
            long elapsedTime = now - lastRefill;


            if (elapsedTime < refillIntervalMillis) { //2nd thread goes here incase race-condition.
                return; // Not time to refill yet
            }

            long tokensToAdd = (elapsedTime/refillIntervalMillis) * refillRate; // 4sec/2 sec * 2 token -->4


                long currentTokens = availableTokens.get();
                long newTokenCount = Math.min(bucket_capacity, currentTokens + tokensToAdd);
                if(availableTokens.compareAndSet(currentTokens,newTokenCount)) { //only one thread will succeed..
                    lastRefillTimestamp.compareAndSet(lastRefill,now);
                   break;
                }
            // CAS failed → retry with new current values
        }



    }

@Override
    public RateLimitHeaderInfo getRateLimitHeaderInfo(HttpServletRequest request)
    {
        RateLimitHeaderInfo rateLimitHeaderInfo =new RateLimitHeaderInfo(getMaxLimit(),getAvailableTokens(),getUsedLimit(),getNextWindowTime());
        return rateLimitHeaderInfo;
    }

    public long getAvailableTokens() {
        return availableTokens.get();
    }

    public long getMaxLimit() {
       return bucket_capacity;
    }
    public long getRemainingLimit() {
       return getAvailableTokens();
    }

    public long getUsedLimit() {
     return  bucket_capacity -getAvailableTokens();
    }
    public long getNextWindowTime() {
        return lastRefillTimestamp.get() + refillIntervalMillis;
    }
}
