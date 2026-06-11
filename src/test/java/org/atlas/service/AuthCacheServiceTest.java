package org.atlas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import org.atlas.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/** Інтеграційні тести реактивного AuthCacheService проти реального Redis. */
@QuarkusTest
class AuthCacheServiceTest extends AbstractIntegrationTest {

    @Inject
    AuthCacheService cacheService;

    @Test
    void generateKey_isDeterministicAndPrefixed() {
        String k1 = cacheService.generateKey("tok", "/path", "GET");
        String k2 = cacheService.generateKey("tok", "/path", "GET");
        String k3 = cacheService.generateKey("tok", "/path", "POST");
        assertEquals(k1, k2);
        assertNotEquals(k1, k3);
        assertTrue(k1.startsWith("auth:"));
    }

    @Test
    void generateKey_handlesNullToken() {
        String k = cacheService.generateKey(null, "/path", "GET");
        assertTrue(k.startsWith("auth:"));
    }

    @Test
    void cacheAndGetDecision_roundtrip() {
        String key = cacheService.generateKey("rt-" + System.nanoTime(), "/p", "GET");
        cacheService.cacheDecision(key, "{\"status\":200}", 30).await().indefinitely();
        String cached = cacheService.getCachedDecision(key).await().indefinitely();
        assertEquals("{\"status\":200}", cached);
    }

    @Test
    void cacheDecision_nonPositiveTtl_isNoOp() {
        String key = cacheService.generateKey("noop-" + System.nanoTime(), "/p", "GET");
        cacheService.cacheDecision(key, "{\"status\":200}", 0).await().indefinitely();
        assertNull(cacheService.getCachedDecision(key).await().indefinitely());
    }
}
