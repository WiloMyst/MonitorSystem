package org.example.monitorsystem.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 服务熔断器
 * 保护 Java 后端对 AI 微服务的调用，防止级联故障:
 *   - CLOSED: 正常状态，允许所有请求
 *   - OPEN: 熔断状态，连续 5 次失败后触发，拒绝所有请求 30s
 *   - HALF_OPEN: 半开状态，允许 2 个探测请求，成功则恢复 CLOSED，失败则重回 OPEN
 */
@Component
public class AiCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(AiCircuitBreaker.class);

    private static final int FAILURE_THRESHOLD = 5;
    private static final long OPEN_DURATION_MS = 30_000;
    private static final int HALF_OPEN_MAX_REQUESTS = 2;

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong openedAt = new AtomicLong(0);
    private final AtomicInteger halfOpenRequests = new AtomicInteger(0);
    private volatile State state = State.CLOSED;

    private enum State { CLOSED, OPEN, HALF_OPEN }

    public boolean allowRequest() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                if (Instant.now().toEpochMilli() - openedAt.get() >= OPEN_DURATION_MS) {
                    state = State.HALF_OPEN;
                    halfOpenRequests.set(0);
                    log.info("[AI熔断器] 从 OPEN 转为 HALF_OPEN，允许探测请求");
                    return true;
                }
                return false;
            case HALF_OPEN:
                return halfOpenRequests.incrementAndGet() <= HALF_OPEN_MAX_REQUESTS;
            default:
                return false;
        }
    }

    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            if (successCount.incrementAndGet() >= HALF_OPEN_MAX_REQUESTS) {
                reset();
                log.info("[AI熔断器] 从 HALF_OPEN 转为 CLOSED，AI 微服务恢复正常");
            }
        } else {
            failureCount.set(0);
        }
    }

    public void recordFailure() {
        int failures = failureCount.incrementAndGet();
        if (state == State.HALF_OPEN) {
            tripBreaker();
            log.warn("[AI熔断器] HALF_OPEN 探测失败，重新进入 OPEN");
        } else if (failures >= FAILURE_THRESHOLD) {
            tripBreaker();
            log.warn("[AI熔断器] 连续 {} 次失败，从 CLOSED 转为 OPEN，熔断 30s", failures);
        }
    }

    public String getState() {
        return state.name();
    }

    private void tripBreaker() {
        state = State.OPEN;
        openedAt.set(Instant.now().toEpochMilli());
        successCount.set(0);
        halfOpenRequests.set(0);
    }

    private void reset() {
        state = State.CLOSED;
        failureCount.set(0);
        successCount.set(0);
        openedAt.set(0);
        halfOpenRequests.set(0);
    }
}
