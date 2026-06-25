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
        public String plan;
        public String email;

        public CacheEntry() {}

        public CacheEntry(int status, String userId, String roles, String plan, String email) {
            this.status = status;
            this.userId = userId;
            this.roles = roles;
            this.plan = plan;
            this.email = email;
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
                        // entry.plan is only a fallback; the live key wins (immediate plan changes).
                        return finalizeResponse(entry.status, entry.userId, entry.roles, entry.plan, entry.email);
                    } catch (Exception e) {
                        return processAndCache(method, path, token, cacheKey);
                    }
                } else {
                    return processAndCache(method, path, token, cacheKey);
                }
            });
    }

    /**
     * Resolves X-User-Plan from the live `plan:<userId>` Redis key (written by the
     * subscription.changed consumer) so plan changes apply on the next /verify. The
     * token's plan claim is only a fallback used to seed the key when it is absent.
     */
    private Uni<Response> finalizeResponse(int status, String userId, String roles, String fallbackPlan, String email) {
        if (status != 200 || userId == null || userId.isBlank()) {
            return Uni.createFrom().item(buildResponse(status, userId, roles, null, email));
        }
        return cacheService.getUserPlan(userId)
            .onItem().transformToUni(livePlan -> {
                if (livePlan != null && !livePlan.isBlank()) {
                    return Uni.createFrom().item(buildResponse(status, userId, roles, livePlan, email));
                }
                if (fallbackPlan != null && !fallbackPlan.isBlank()) {
                    // Seed the live key (NX so a fresher consumer write is never clobbered).
                    return cacheService.seedUserPlan(userId, fallbackPlan)
                        .onItem().transform(v -> buildResponse(status, userId, roles, fallbackPlan, email));
                }
                return Uni.createFrom().item(buildResponse(status, userId, roles, null, email));
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
        String plan = null;
        String email = null;
        long tokenExpSeconds = -1;
        int status = 401; // Default to unauthenticated

        if (token != null) {
            try {
                JsonWebToken jwt = jwtParser.parse(token);
                userId = jwt.getSubject();

                Object planClaim = jwt.claim("plan").orElse(null);
                if (planClaim != null) {
                    plan = planClaim.toString();
                }

                Object emailClaim = jwt.claim("email").orElse(null);
                if (emailClaim != null) {
                    email = emailClaim.toString();
                }

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
        CacheEntry entry = new CacheEntry(status, userId, rolesStr, plan, email);
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
        String finalPlan = plan;
        String finalEmail = email;
        return cacheService.cacheDecision(cacheKey, json, ttl)
            .onItem().transformToUni(v -> finalizeResponse(finalStatus, finalUserId, rolesStr, finalPlan, finalEmail));
    }

    private Response buildResponse(int status, String userId, String roles, String plan, String email) {
        Response.ResponseBuilder builder = Response.status(status);
        if (status == 200) {
            // Traefik expects the headers so it can pass them downstream
            if (userId != null && !userId.isBlank()) {
                builder.header("X-User-Id", userId);
            }
            if (roles != null && !roles.isBlank()) {
                builder.header("X-User-Role", roles);
            }
            if (plan != null && !plan.isBlank()) {
                builder.header("X-User-Plan", plan);
            }
            if (email != null && !email.isBlank()) {
                builder.header("X-User-Email", email);
            }
        }
        return builder.build();
    }
}
