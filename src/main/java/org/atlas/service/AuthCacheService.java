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
}
