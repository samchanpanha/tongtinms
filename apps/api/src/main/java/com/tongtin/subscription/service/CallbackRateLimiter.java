package com.tongtin.subscription.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window limiter for the public PayWay webhook callback,
 * keyed by client IP (same window mechanics as the auth rate-limit filter).
 */
@Component
public class CallbackRateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean allow(String ip, int limit, Duration windowTtl) {
        Instant now = Instant.now();
        Window window = windows.compute(ip, (key, existing) ->
                existing == null || existing.expiresAt.isBefore(now) ? new Window(now.plus(windowTtl)) : existing);
        synchronized (window) {
            if (window.expiresAt.isBefore(now)) {
                window.expiresAt = now.plus(windowTtl);
                window.count.set(0);
            }
            return window.count.incrementAndGet() <= limit;
        }
    }

    private static final class Window {
        private volatile Instant expiresAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
