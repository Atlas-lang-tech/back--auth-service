package org.atlas.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.atlas.dto.AuthDto.*;
import org.atlas.service.AuthService;
import org.atlas.service.EmailService;
import org.atlas.service.JwtService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth", description = "Registration, login, tokens")
public class AuthResource {

	@Inject
	AuthService authService;

	@Inject
	JwtService jwtService;

	@Inject
	EmailService emailService;

	@POST
	@Path("/register")
	@PermitAll
	@Operation(summary = "Register new user")
	public Response register(@Valid RegisterRequest request) {
		TokenResponse response = authService.register(request);
		return Response.status(Response.Status.CREATED).entity(response).build();
	}

	@POST
	@Path("/login")
	@PermitAll
	@Operation(summary = "User login")
	public Response login(@Valid LoginRequest request) {
		TokenResponse response = authService.login(request);
		return Response.ok(response).build();
	}

	@POST
	@Path("/refresh")
	@PermitAll
	@Operation(summary = "Renew access token")
	public Response refresh(@Valid RefreshRequest request) {
		TokenResponse response = authService.refresh(request);
		return Response.ok(response).build();
	}

	@POST
	@Path("/logout")
	@RolesAllowed({ "USER", "ADMIN" })
	@Operation(summary = "Log out from system")
	public Response logout() {
		String userId = jwtService.getSubject();
		authService.logout(userId);
		return Response.noContent().build();
	}

	@GET
	@Path("/me")
	@RolesAllowed({ "USER" })
	@Operation(summary = "Current user")
	public Response getProfile() {
		String userId = jwtService.getSubject();
		UserResponse profile = authService.getProfile(userId);
		return Response.ok(profile).build();
	}

	@POST
	@Path("/email")
	@PermitAll
	@Operation(summary = "Send email")
	public Response sendEmail() {
		emailService.main();
		return Response.noContent().build();
	}
}
