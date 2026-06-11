package org.atlas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import org.atlas.entity.User;
import io.quarkus.test.junit.QuarkusTest;
import org.atlas.test.AbstractIntegrationTest;
import org.atlas.test.TestFixtures;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

/** Інтеграційні тести JwtService — реальний підпис/верифікація на тестових ключах. */
@QuarkusTest
class JwtServiceTest extends AbstractIntegrationTest {

    @Inject
    JwtService jwtService;

    @Inject
    JWTParser parser;

    @Test
    void generateAccessToken_carriesUserClaims() throws Exception {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "alice@example.com", User.Role.ADMIN);

        String token = jwtService.generateAccessToken(user);
        JsonWebToken parsed = parser.parse(token);

        assertEquals(id.toString(), parsed.getSubject());
        assertEquals("https://atlas.org/auth", parsed.getIssuer());
        assertTrue(parsed.getGroups().contains("ADMIN"));
        assertEquals("alice@example.com", parsed.getClaim("email"));
        assertEquals(id.toString(), parsed.getClaim("id").toString());

        long now = Instant.now().getEpochSecond();
        assertTrue(parsed.getExpirationTime() > now);
        assertTrue(parsed.getExpirationTime() <= now + 900 + 5);
    }

    @Test
    void generateRefreshToken_isMarkedAsRefresh() throws Exception {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "bob@example.com", User.Role.USER);

        String token = jwtService.generateRefreshToken(user);
        JsonWebToken parsed = parser.parse(token);

        assertEquals(id.toString(), parsed.getSubject());
        assertEquals("refresh", parsed.getClaim("type"));
        assertNotNull(parsed.getClaim("jti"));
        assertTrue(jwtService.isRefreshToken(parsed));

        long now = Instant.now().getEpochSecond();
        assertTrue(parsed.getExpirationTime() > now + 600000);
    }

    @Test
    void isRefreshToken_falseForAccessToken() throws Exception {
        User user = TestFixtures.transientUser(UUID.randomUUID(), "c@example.com", User.Role.USER);
        JsonWebToken access = parser.parse(jwtService.generateAccessToken(user));
        assertFalse(jwtService.isRefreshToken(access));
    }

    @Test
    void expiryGetters_returnConfiguredValues() {
        assertEquals(900L, jwtService.getAccessTokenExpiry());
        assertEquals(604800L, jwtService.getRefreshTokenExpiry());
    }
}
