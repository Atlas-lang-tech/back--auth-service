package org.atlas.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.atlas.service.ForwardAuthService;

/**
 * Endpoint called by Traefik ForwardAuth plugin.
 * It will intercept incoming requests to other services.
 */
@Path("/verify")
public class ForwardAuthResource {

    @Inject
    ForwardAuthService authService;

    // Use a catch-all method to allow Traefik to forward requests natively
    // We use @GET as Traefik conventionally uses GET for its forwardauth verify calls,
    // sending X-Forwarded-Method for the actual HTTP method requested.
    @GET
    public Uni<Response> verify(@Context HttpHeaders headers) {
        String authHeader = headers.getHeaderString("Authorization");
        String forwardedUri = headers.getHeaderString("X-Forwarded-Uri");
        String forwardedMethod = headers.getHeaderString("X-Forwarded-Method");

        return authService.verify(forwardedMethod, forwardedUri, authHeader);
    }
}
