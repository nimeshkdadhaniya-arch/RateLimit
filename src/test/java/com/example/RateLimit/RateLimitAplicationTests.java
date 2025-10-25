package com.example.RateLimit;

import com.example.ratelimit.RateLimitApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = RateLimitApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RateLimitAplicationTests {

	@Test
	void contextLoads() {
	}


}
