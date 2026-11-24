package com.bzd.balance.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bzd.balance.common.BizException;

/**
 * Small in-memory login throttle. Keys combine normalized username and remote
 * address so one account cannot be brute-forced from a single client.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final int CLEANUP_THRESHOLD = 1000;

    private final int maxAttempts;
    private final Duration window;
    private final Duration lockDuration;
    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.security.login-max-attempts:5}") int maxAttempts,
            @Value("${app.security.login-window-minutes:15}") long windowMinutes,
            @Value("${app.security.login-lock-minutes:15}") long lockMinutes) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.window = Duration.ofMinutes(Math.max(1, windowMinutes));
        this.lockDuration = Duration.ofMinutes(Math.max(1, lockMinutes));
    }

    public void assertAllowed(String username, String ip) {
        AttemptState state = attempts.get(key(username, ip));
        if (state == null) {
            return;
        }
        Instant now = Instant.now();
        synchronized (state) {
            if (state.lockedUntil != null && state.lockedUntil.isAfter(now)) {
                throw new BizException(429, "登录尝试过多，请稍后再试");
            }
            if (state.lockedUntil != null || now.isAfter(state.windowStarted.plus(window))) {
                attempts.remove(key(username, ip), state);
            }
        }
    }

    public void recordFailure(String username, String ip) {
        String key = key(username, ip);
        Instant now = Instant.now();
        AttemptState state = attempts.computeIfAbsent(key, ignored -> new AttemptState(now));
        synchronized (state) {
            if (now.isAfter(state.windowStarted.plus(window))) {
                state.failures = 0;
                state.windowStarted = now;
                state.lockedUntil = null;
            }
            state.failures++;
            if (state.failures >= maxAttempts) {
                state.lockedUntil = now.plus(lockDuration);
                log.warn("登录失败次数达到上限：user={}, ip={}, failures={}",
                        normalizeUsername(username), ip, state.failures);
            }
        }
        if (attempts.size() > CLEANUP_THRESHOLD) {
            removeExpired(now);
        }
    }

    public void recordSuccess(String username, String ip) {
        attempts.remove(key(username, ip));
    }

    private void removeExpired(Instant now) {
        attempts.entrySet().removeIf(entry -> {
            AttemptState state = entry.getValue();
            synchronized (state) {
                Instant expires = state.lockedUntil == null
                        ? state.windowStarted.plus(window)
                        : state.lockedUntil;
                return now.isAfter(expires);
            }
        });
    }

    private String key(String username, String ip) {
        return normalizeUsername(username) + "|" + (ip == null ? "" : ip);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static final class AttemptState {
        private int failures;
        private Instant windowStarted;
        private Instant lockedUntil;

        private AttemptState(Instant started) {
            this.windowStarted = started;
        }
    }
}
