package org.atlas.service;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@ApplicationScoped
public class AuthCacheService {

    private final ReactiveValueCommands<String, String> valueCommands;

    @Inject
    public AuthCacheService(ReactiveRedisDataSource reactiveRedisDataSource) {
        this.valueCommands = reactiveRedisDataSource.value(String.class);
    }

    public String generateKey(String token, String path, String method) {
        try {
            String input = (token == null ? "" : token) + "|" + path + "|" + method;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return "auth:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public Uni<String> getCachedDecision(String key) {
        return valueCommands.get(key);
    }

    public Uni<Void> cacheDecision(String key, String decisionJson, long ttlInSeconds) {
        if (ttlInSeconds <= 0) {
            return Uni.createFrom().voidItem();
        }
        return valueCommands.set(key, decisionJson, new SetArgs().ex(Duration.ofSeconds(ttlInSeconds)));
    }

    // --- Live per-user plan ------------------------------------------------
    // The plan served as X-User-Plan is resolved from this key, not the JWT
    // claim, so a `subscription.changed` event takes effect on the next /verify
    // instead of waiting for the user's access token to be refreshed. The key is
    // overwritten by the subscription consumer (authoritative) and seeded here
    // from the token claim only when absent (NX), so we never clobber a fresher
    // value.

    private static final String USER_PLAN_PREFIX = "plan:";

    public Uni<String> getUserPlan(String userId) {
        return valueCommands.get(USER_PLAN_PREFIX + userId);
    }

    public Uni<Void> seedUserPlan(String userId, String plan) {
        return valueCommands.set(USER_PLAN_PREFIX + userId, plan, new SetArgs().nx());
    }
}
