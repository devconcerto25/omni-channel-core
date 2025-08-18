package com.concerto.omnichannel.configManager;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .failureRateThreshold(50)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);

        // Channel-specific configurations
        registry.circuitBreaker("BBPS-fetchBill", CircuitBreakerConfig.custom()
                .slidingWindowSize(8)
                .failureRateThreshold(60)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .build());

        registry.circuitBreaker("ISO8583-purchase", CircuitBreakerConfig.custom()
                .slidingWindowSize(12)
                .failureRateThreshold(40)
                .build());

        return registry;
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig defaultConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1000))
               // .exponentialBackoffMultiplier(2)
                .build();

        RetryRegistry registry = RetryRegistry.of(defaultConfig);

        // Channel-specific configurations
        registry.retry("authentication", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(500))
                .build());

        registry.retry("BBPS-fetchBill", RetryConfig.custom()
                .maxAttempts(2)
                .build());

        registry.retry("ISO8583-purchase", RetryConfig.custom()
                .maxAttempts(1)
                .build());

        return registry;
    }

    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        TimeLimiterConfig defaultConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .cancelRunningFuture(true)
                .build();

        TimeLimiterRegistry registry = TimeLimiterRegistry.of(defaultConfig);

        // Channel-specific configurations
        registry.timeLimiter("authentication", TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(2))
                .build());

        registry.timeLimiter("BBPS-fetchBill", TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(3))
                .build());

        registry.timeLimiter("ISO8583-purchase", TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .build());

        return registry;
    }
}
