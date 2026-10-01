package com.agentflow.backend.run.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/** Redis is only a short-lived execution lease; MySQL remains the run state source. */
@Service
public class RunExecutionLockService {

	private static final String KEY_PREFIX = "agentflow:run:lock:";
	private static final RedisScript<Long> UNLOCK = RedisScript.of(
			"if redis.call('GET', KEYS[1]) == ARGV[1] then "
					+ "return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);
	private static final RedisScript<Long> RENEW = RedisScript.of(
			"if redis.call('GET', KEYS[1]) == ARGV[1] then "
					+ "return redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2])) else return 0 end", Long.class);

	private final StringRedisTemplate redis;
	private final long ttlSeconds;

	public RunExecutionLockService(StringRedisTemplate redis,
			@Value("${agent.run.lock-ttl-seconds:900}") long ttlSeconds) {
		if (ttlSeconds < 30) {
			throw new IllegalArgumentException("Agent run lock TTL must be at least 30 seconds");
		}
		this.redis = redis;
		this.ttlSeconds = ttlSeconds;
	}

	public Optional<Lease> tryAcquire(Long runId) {
		String key = key(runId);
		String token = UUID.randomUUID().toString();
		Boolean acquired = redis.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(ttlSeconds));
		return Boolean.TRUE.equals(acquired) ? Optional.of(new Lease(key, token)) : Optional.empty();
	}

	public boolean renew(Lease lease) {
		Long renewed = redis.execute(RENEW, List.of(lease.key()), lease.token(), Long.toString(ttlSeconds));
		return Long.valueOf(1).equals(renewed);
	}

	public void release(Lease lease) {
		redis.execute(UNLOCK, List.of(lease.key()), lease.token());
	}

	public boolean isHeld(Long runId) {
		return Boolean.TRUE.equals(redis.hasKey(key(runId)));
	}

	public long renewalIntervalSeconds() {
		return Math.min(60, Math.max(10, ttlSeconds / 3));
	}

	private String key(Long runId) {
		return KEY_PREFIX + runId;
	}

	public record Lease(String key, String token) { }
}
