package org.atlas.service;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.string.StringCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RedisService {

	private static final String REFRESH_TOKEN_PREFIX = "refresh:";
	private static final String BLACKLIST_PREFIX = "blacklist:";
	private static final String EVENT_PREFIX = "event:";
	private static final String USER_PLAN_PREFIX = "plan:";

	private final StringCommands<String, String> commands;

	@Inject
	public RedisService(RedisDataSource redisDataSource) {
		this.commands = redisDataSource.string(String.class);
	}

	public void saveRefreshToken(String userId, String token, long ttlSeconds) {
		commands.setex(REFRESH_TOKEN_PREFIX + userId, ttlSeconds, token);
	}

	public String getRefreshToken(String userId) {
		return commands.get(REFRESH_TOKEN_PREFIX + userId);
	}

	public void deleteRefreshToken(String userId) {
		commands.getdel(REFRESH_TOKEN_PREFIX + userId);
	}

	public boolean isRefreshTokenValid(String userId, String token) {
		String stored = getRefreshToken(userId);
		return token.equals(stored);
	}

	public void blacklistToken(String jti, long ttlSeconds) {
		commands.setex(BLACKLIST_PREFIX + jti, ttlSeconds, "1");
	}

	public boolean isTokenBlacklisted(String jti) {
		return commands.get(BLACKLIST_PREFIX + jti) != null;
	}

	// --- Ідемпотентність споживання подій (дедуп за messageId) ---

	public boolean isEventProcessed(String messageId) {
		return commands.get(EVENT_PREFIX + messageId) != null;
	}

	public void markEventProcessed(String messageId, long ttlSeconds) {
		commands.setex(EVENT_PREFIX + messageId, ttlSeconds, "1");
	}

	// --- Live per-user plan (read by ForwardAuth to emit X-User-Plan) ---

	/** Authoritative write of a user's current plan from subscription.changed. */
	public void setUserPlan(String userId, String plan) {
		commands.set(USER_PLAN_PREFIX + userId, plan);
	}
}
