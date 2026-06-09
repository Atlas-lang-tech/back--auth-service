package org.atlas.resource;

import java.util.List;

import org.atlas.dto.AuthDto.UserResponse;
import org.atlas.service.AuthService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Users", description = "User management")
public class UserResource {

    @Inject
    AuthService authService;

    // ------------------------------------------------------------------ //
    //  LIST ALL USERS
    // ------------------------------------------------------------------ //

    @GET
    @Authenticated
    @Operation(summary = "Get all users")
    public Response getAllUsers() {
        List<UserResponse> users = authService.getAllUsers();
        return Response.ok(users).build();
    }
}
