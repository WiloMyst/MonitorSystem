package org.example.monitorsystem.core.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 服务限流器
 * 基于 Caffeine 缓存 + 滑动窗口算法，按用户维度进行双层限流:
 *   - 秒级限流: 每用户每秒最多 3 次
 *   - 分钟级限流: 每用户每分钟最多 10 次
 */
@Component
public class AiRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AiRateLimiter.class);

    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final int MAX_REQUESTS_PER_SECOND = 3;

    private final Cache<String, SlidingWindow> minuteWindows = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(2))
            .maximumSize(10000)
            .build();

    private final Cache<String, SlidingWindow> secondWindows = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofSeconds(5))
            .maximumSize(10000)
            .build();

    public boolean tryAcquire(String clientId) {
        SlidingWindow minuteWindow = minuteWindows.get(clientId, k -> new SlidingWindow());
        SlidingWindow secondWindow = secondWindows.get(clientId, k -> new SlidingWindow());

        Instant now = Instant.now();

        long minuteCount = minuteWindow.count(now, 60_000);
        if (minuteCount >= MAX_REQUESTS_PER_MINUTE) {
            log.warn("[AI限流] 用户 {} 分钟级限流触发: {}/min", clientId, minuteCount);
            return false;
        }

        long secondCount = secondWindow.count(now, 1_000);
        if (secondCount >= MAX_REQUESTS_PER_SECOND) {
            log.warn("[AI限流] 用户 {} 秒级限流触发: {}/s", clientId, secondCount);
            return false;
        }

        minuteWindow.add(now);
        secondWindow.add(now);
        return true;
    }

    private static class SlidingWindow {
        private final long[] timestamps = new long[200];
        private final AtomicLong index = new AtomicLong(0);

        synchronized void add(Instant now) {
            int idx = (int) (index.getAndIncrement() % timestamps.length);
            timestamps[idx] = now.toEpochMilli();
        }

        synchronized long count(Instant now, long windowMs) {
            long threshold = now.toEpochMilli() - windowMs;
            long count = 0;
            for (long ts : timestamps) {
                if (ts >= threshold && ts > 0) {
                    count++;
                }
            }
            return count;
        }
    }
}
