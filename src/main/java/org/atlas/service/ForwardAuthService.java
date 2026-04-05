package org.atlas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

@ApplicationScoped
public class ForwardAuthService {

    @Inject
    JWTParser jwtParser;

    @Inject
    AuthCacheService cacheService;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "atlas.forwardauth.cache-ttl", defaultValue = "60")
    long defaultCacheTtl;

    public static class CacheEntry {
        public int status;
        public String userId;
        public String roles;
        
        public CacheEntry() {}
        
        public CacheEntry(int status, String userId, String roles) {
            this.status = status;
            this.userId = userId;
            this.roles = roles;
        }
    }

    public Uni<Response> verify(String method, String path, String authHeader) {
        String token = extractToken(authHeader);
        String cacheKey = cacheService.generateKey(token, path, method);

        return cacheService.getCachedDecision(cacheKey)
            .onItem().transformToUni(cachedValue -> {
                if (cachedValue != null) {
                    try {
                        CacheEntry entry = objectMapper.readValue(cachedValue, CacheEntry.class);
                        return Uni.createFrom().item(buildResponse(entry.status, entry.userId, entry.roles));
                    } catch (Exception e) {
                        return processAndCache(method, path, token, cacheKey);
                    }
                } else {
                    return processAndCache(method, path, token, cacheKey);
                }
            });
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private Uni<Response> processAndCache(String method, String path, String token, String cacheKey) {
        String userId = null;
        List<String> rolesList = Collections.emptyList();
        long tokenExpSeconds = -1;
        int status = 401; // Default to unauthenticated

        if (token != null) {
            try {
                JsonWebToken jwt = jwtParser.parse(token);
                userId = jwt.getSubject();
                
                Set<String> groups = jwt.getGroups();
                if (groups != null && !groups.isEmpty()) {
                    rolesList = groups.stream().toList();
                } else {
                    Object roleClaim = jwt.claim("role").orElse(null);
                    if (roleClaim != null && !roleClaim.toString().isEmpty()) {
                        rolesList = List.of(roleClaim.toString());
                    }
                }
                
                Long exp = jwt.getExpirationTime();
                if (exp != null) {
                    long now = System.currentTimeMillis() / 1000;
                    tokenExpSeconds = exp - now;
                }
                
                // If parsing succeeded and wasn't expired
                status = 200;
            } catch (ParseException e) {
                // Invalid or expired token
                status = 401; 
            }
        }

        String rolesStr = String.join(",", rolesList);
        CacheEntry entry = new CacheEntry(status, userId, rolesStr);
        String json;
        try {
            json = objectMapper.writeValueAsString(entry);
        } catch (Exception e) {
            json = "{\"status\":" + status + "}";
        }

        long ttl = defaultCacheTtl;
        if (tokenExpSeconds > 0 && tokenExpSeconds < defaultCacheTtl) {
            ttl = tokenExpSeconds;
        }

        int finalStatus = status;
        String finalUserId = userId;
        return cacheService.cacheDecision(cacheKey, json, ttl)
            .onItem().transform(v -> buildResponse(finalStatus, finalUserId, rolesStr));
    }

    private Response buildResponse(int status, String userId, String roles) {
        Response.ResponseBuilder builder = Response.status(status);
        if (status == 200) {
            // Traefik expects the headers so it can pass them downstream
            if (userId != null && !userId.isBlank()) {
                builder.header("X-User-Id", userId);
            }
            if (roles != null && !roles.isBlank()) {
                builder.header("X-User-Role", roles);
            }
        }
        return builder.build();
    }
}
