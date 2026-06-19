package org.atlas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.mutiny.Uni;
import java.util.Optional;
import java.util.Set;
import jakarta.ws.rs.core.Response;
import org.atlas.service.ForwardAuthService.CacheEntry;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Юніт-тести ForwardAuthService на Mockito (реальний ObjectMapper). */
class ForwardAuthServiceTest {

    ForwardAuthService service;
    JWTParser jwtParser;
    AuthCacheService cacheService;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ForwardAuthService();
        jwtParser = mock(JWTParser.class);
        cacheService = mock(AuthCacheService.class);
        service.jwtParser = jwtParser;
        service.cacheService = cacheService;
        service.objectMapper = objectMapper;
        service.defaultCacheTtl = 60;

        when(cacheService.generateKey(any(), anyString(), anyString())).thenReturn("cache-key");
        when(cacheService.cacheDecision(anyString(), anyString(), anyLong()))
            .thenReturn(Uni.createFrom().voidItem());
    }

    private Response doVerify(String method, String path, String authHeader) {
        return service.verify(method, path, authHeader).await().indefinitely();
    }

    @Test
    void verify_cacheHit_returnsCachedDecision() throws Exception {
        String json = objectMapper.writeValueAsString(new CacheEntry(200, "user-1", "ADMIN", "PRO"));
        when(cacheService.getCachedDecision("cache-key")).thenReturn(Uni.createFrom().item(json));

        Response res = doVerify("GET", "/x", "Bearer tok");

        assertEquals(200, res.getStatus());
        assertEquals("user-1", res.getHeaderString("X-User-Id"));
        assertEquals("ADMIN", res.getHeaderString("X-User-Role"));
        assertEquals("PRO", res.getHeaderString("X-User-Plan"));
        // при кеш-хіті токен не парситься
        verify(cacheService, org.mockito.Mockito.never()).cacheDecision(anyString(), anyString(), anyLong());
    }

    @Test
    void verify_cacheMiss_validToken_includesPlanHeader() throws Exception {
        when(cacheService.getCachedDecision("cache-key")).thenReturn(Uni.createFrom().nullItem());

        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn("user-77");
        when(jwt.getGroups()).thenReturn(Set.of("USER"));
        when(jwt.claim("plan")).thenReturn(Optional.of("PRO"));
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() / 1000 + 3600);
        when(jwtParser.parse("tok")).thenReturn(jwt);

        Response res = doVerify("GET", "/secure", "Bearer tok");

        assertEquals(200, res.getStatus());
        assertEquals("PRO", res.getHeaderString("X-User-Plan"));
    }

    @Test
    void verify_cacheMiss_noPlanClaim_omitsPlanHeader() throws Exception {
        when(cacheService.getCachedDecision("cache-key")).thenReturn(Uni.createFrom().nullItem());

        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn("user-88");
        when(jwt.getGroups()).thenReturn(Set.of("USER"));
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() / 1000 + 3600);
        when(jwtParser.parse("tok")).thenReturn(jwt);

        Response res = doVerify("GET", "/secure", "Bearer tok");

        assertEquals(200, res.getStatus());
        assertNull(res.getHeaderString("X-User-Plan"));
    }

    @Test
    void verify_cacheMiss_validToken_returns200WithHeaders() throws Exception {
        when(cacheService.getCachedDecision("cache-key")).thenReturn(Uni.createFrom().nullItem());

        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn("user-42");
        when(jwt.getGroups()).thenReturn(Set.of("ADMIN"));
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() / 1000 + 3600);
        when(jwtParser.parse("tok")).thenReturn(jwt);

        Response res = doVerify("GET", "/secure", "Bearer tok");

        assertEquals(200, res.getStatus());
        assertEquals("user-42", res.getHeaderString("X-User-Id"));
        assertEquals("ADMIN", res.getHeaderString("X-User-Role"));

        // TTL обмежений defaultCacheTtl=60 (бо токен живе 3600)
        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(cacheService).cacheDecision(eq("cache-key"), anyString(), ttl.capture());
        assertEquals(60L, ttl.getValue());
    }

    @Test
    void verify_cacheMiss_roleFromClaim_whenNoGroups() throws Exception {
        when(cacheService.getCachedDecision("cache-key")).thenReturn(Uni.createFrom().nullItem());

        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn("user-7");
        when(jwt.getGroups()).thenReturn(Set.of());
        when(jwt.claim("role")).thenReturn(Optional.of("USER"));
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() / 1000 + 10);
        when(jwtParser.parse("tok")).thenReturn(jwt);

        Response res = doVerify("GET", "/x", "Bearer tok");
        assertEquals(200, res.getStatus());
        assertEquals("USER", res.getHeaderString("X-User-Role"));

        // токен живе 10с < 60с → TTL = 10
        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(cacheService).cacheDecision(eq("cache-key"), anyString(), ttl.capture());
        assertEquals(10L, ttl.getValue());
    }

    @Test
    void verify_noToken_returns401() {
        when(cacheService.getCachedDecision("cache-key")).thenReturn(Uni.createFrom().nullItem());
        Response res = doVerify("GET", "/x", null);
        assertEquals(401, res.getStatus());
        assertNull(res.getHeaderString("X-User-Id"));
    }

    @Test
    void verify_nonBearerHeader_treatedAsNoToken() {
        when(cacheService.getCachedDecision("cache-key")).thenReturn(Uni.createFrom().nullItem());
        Response res = doVerify("GET", "/x", "Basic abc");
        assertEquals(401, res.getStatus());
    }

    @Test
    void verify_invalidToken_returns401() throws Exception {
        when(cacheService.getCachedDecision("cache-key")).thenReturn(Uni.createFrom().nullItem());
        when(jwtParser.parse("tok")).thenThrow(new ParseException("bad"));
        Response res = doVerify("GET", "/x", "Bearer tok");
        assertEquals(401, res.getStatus());
    }

    @Test
    void verify_corruptedCacheValue_reprocesses() throws Exception {
        when(cacheService.getCachedDecision("cache-key")).thenReturn(Uni.createFrom().item("}{not-json"));
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn("user-9");
        when(jwt.getGroups()).thenReturn(Set.of("USER"));
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() / 1000 + 100);
        when(jwtParser.parse("tok")).thenReturn(jwt);

        Response res = doVerify("GET", "/x", "Bearer tok");
        assertEquals(200, res.getStatus());
        assertTrue(res.getHeaderString("X-User-Id").equals("user-9"));
        verify(cacheService).cacheDecision(anyString(), anyString(), anyLong());
    }
}
