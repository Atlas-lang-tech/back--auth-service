package org.atlas.service;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.string.StringCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RedisService {

	private static final String REFRESH_TOKEN_PREFIX = "refresh:";
	private static final String BLACKLIST_PREFIX = "blacklist:";

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
}
