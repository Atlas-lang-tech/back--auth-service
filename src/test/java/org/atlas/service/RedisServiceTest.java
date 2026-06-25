package org.atlas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;
import java.util.UUID;
import io.quarkus.test.junit.QuarkusTest;
import org.atlas.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/** Інтеграційні тести RedisService проти реального Redis (Testcontainers). */
@QuarkusTest
class RedisServiceTest extends AbstractIntegrationTest {

    @Inject
    RedisService redisService;

    @Test
    void saveAndGetRefreshToken_roundtrip() {
        String userId = UUID.randomUUID().toString();
        redisService.saveRefreshToken(userId, "tok-abc", 60);
        assertEquals("tok-abc", redisService.getRefreshToken(userId));
    }

    @Test
    void isRefreshTokenValid_matchesStoredValue() {
        String userId = UUID.randomUUID().toString();
        redisService.saveRefreshToken(userId, "tok-xyz", 60);
        assertTrue(redisService.isRefreshTokenValid(userId, "tok-xyz"));
        assertFalse(redisService.isRefreshTokenValid(userId, "other"));
    }

    @Test
    void deleteRefreshToken_removesValue() {
        String userId = UUID.randomUUID().toString();
        redisService.saveRefreshToken(userId, "tok", 60);
        redisService.deleteRefreshToken(userId);
        assertNull(redisService.getRefreshToken(userId));
        assertFalse(redisService.isRefreshTokenValid(userId, "tok"));
    }

    @Test
    void blacklist_marksAndReportsToken() {
        String jti = UUID.randomUUID().toString();
        assertFalse(redisService.isTokenBlacklisted(jti));
        redisService.blacklistToken(jti, 60);
        assertTrue(redisService.isTokenBlacklisted(jti));
    }

    @Test
    void verificationToken_consumeReturnsUserIdOnceThenNull() {
        String token = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        redisService.saveVerificationToken(token, userId, 60);
        assertEquals(userId, redisService.consumeVerificationToken(token));
        // Одноразовий — другий виклик уже null
        assertNull(redisService.consumeVerificationToken(token));
    }

    @Test
    void resetToken_consumeReturnsUserIdOnceThenNull() {
        String token = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        redisService.saveResetToken(token, userId, 60);
        assertEquals(userId, redisService.consumeResetToken(token));
        assertNull(redisService.consumeResetToken(token));
    }
}
