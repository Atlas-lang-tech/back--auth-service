package org.atlas.resource;

import org.atlas.dto.AuthDto.LoginRequest;
import org.atlas.dto.AuthDto.LoginResponse;
import org.atlas.dto.AuthDto.RefreshRequest;
import org.atlas.dto.AuthDto.RegisterRequest;
import org.atlas.dto.AuthDto.TokenResponse;
import org.atlas.dto.AuthDto.UserResponse;
import org.atlas.service.AuthService;
import org.atlas.service.EmailService;
import org.atlas.service.JwtService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
		return Response.status(Response.Status.CREATED).build();
	}

	@POST
	@Path("/login")
	@PermitAll
	@Operation(summary = "User login")
	public Response login(@Valid LoginRequest request) {
		TokenResponse tokenResponse = authService.login(request);
		UserResponse userResponse = authService.getProfile(request.email());
		LoginResponse loginResponse = new LoginResponse(tokenResponse, userResponse);
		return Response.ok(loginResponse)
			.header("Set-Cookie", createRefreshTokenCookie(tokenResponse.refreshToken()))
			.build();
	}

	@POST
	@Path("/refresh")
	@PermitAll
	@Operation(summary = "Renew access token")
	public Response refresh(@Valid RefreshRequest request) {
		TokenResponse tokenResponse = authService.refresh(request);
		return Response.ok(tokenResponse)
			.header("Set-Cookie", createRefreshTokenCookie(tokenResponse.refreshToken()))
			.build();
	}

	@POST
	@Path("/logout")
	@RolesAllowed({ "USER", "ADMIN" })
	@Operation(summary = "Log out from system")
	public Response logout() {
		String userId = jwtService.getSubject();
		authService.logout(userId);
		return Response.noContent()
			.header("Set-Cookie", clearRefreshTokenCookie())
			.build();
	}

	private String createRefreshTokenCookie(String refreshToken) {
		return "refreshToken=" + refreshToken + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + jwtService.getRefreshTokenExpiry();
	}

	private String clearRefreshTokenCookie() {
		return "refreshToken=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT";
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
