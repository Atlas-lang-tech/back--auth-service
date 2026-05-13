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
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth", description = "Registration, login, tokens")
public class AuthResource {

    // Назва куки — одне місце, щоб не помилитись
    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    @Inject
    AuthService authService;

    @Inject
    JwtService jwtService;

    @Inject
    EmailService emailService;

    // ------------------------------------------------------------------ //
    //  REGISTER
    // ------------------------------------------------------------------ //

    @POST
    @Path("/register")
    @PermitAll
    @Operation(summary = "Register new user")
    public Response register(@Valid RegisterRequest request) {
        authService.register(request);
        return Response.status(Response.Status.CREATED).build();
    }

    // ------------------------------------------------------------------ //
    //  LOGIN
    // ------------------------------------------------------------------ //

    @POST
    @Path("/login")
    @PermitAll
    @Operation(summary = "User login")
    public Response login(@Valid LoginRequest request) {
        TokenResponse tokenResponse = authService.login(request);
        UserResponse userResponse  = authService.getProfile(request.email());
        LoginResponse loginResponse = new LoginResponse(tokenResponse, userResponse);

        return Response.ok(loginResponse)
                .cookie(buildRefreshCookie(tokenResponse.refreshToken()))
                .build();
    }

    // ------------------------------------------------------------------ //
    //  REFRESH
    // ------------------------------------------------------------------ //

    @POST
    @Path("/refresh")
    @PermitAll
    @Operation(summary = "Renew access token")
    public Response refresh(@Valid RefreshRequest request) {
        TokenResponse tokenResponse = authService.refresh(request);

        return Response.ok(tokenResponse)
                .cookie(buildRefreshCookie(tokenResponse.refreshToken()))
                .build();
    }

    // ------------------------------------------------------------------ //
    //  LOGOUT
    // ------------------------------------------------------------------ //

    @POST
    @Path("/logout")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Log out from system")
    public Response logout() {
        String userId = jwtService.getSubject();
        authService.logout(userId);

        return Response.noContent()
                .cookie(clearRefreshCookie())
                .build();
    }

    // ------------------------------------------------------------------ //
    //  EMAIL (тест)
    // ------------------------------------------------------------------ //

    @POST
    @Path("/email")
    @PermitAll
    @Operation(summary = "Send email")
    public Response sendEmail() {
        emailService.main();
        return Response.noContent().build();
    }

    // ================================================================== //
    //  HELPERS
    // ================================================================== //

    /**
     * Будує HttpOnly куку для refreshToken.
     *
     * Локально (HTTP):
     *   - SameSite=Lax  — браузер надсилає куку при навігації з того самого сайту
     *   - без Secure    — HTTP не підтримує Secure
     *   - без SameSite=None — None вимагає Secure, тому не підходить для localhost
     *
     * На продакшні (HTTPS) замінити рядок на:
     *   "; Path=/; HttpOnly; Secure; SameSite=None; Max-Age=" + maxAge
     */
    private NewCookie buildRefreshCookie(String refreshToken) {
        int maxAge = (int) jwtService.getRefreshTokenExpiry();

        // JAX-RS NewCookie не підтримує SameSite — додаємо через коментар-поле (comment),
        // яке Quarkus/RESTEasy прокидає як додатковий атрибут у Set-Cookie заголовку.
        // Альтернатива — header("Set-Cookie", ...) нижче у вигляді рядка.
        return new NewCookie.Builder(REFRESH_COOKIE_NAME)
                .value(refreshToken)
                .path("/")
                .maxAge(maxAge)
                .httpOnly(true)
                // Secure = false для localhost (HTTP)
                .secure(false)
                // SameSite через розширений конструктор (RESTEasy 6.x / Quarkus 3.x)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }

    /**
     * Очищує куку при logout — Max-Age=0, порожнє значення.
     */
    private NewCookie clearRefreshCookie() {
        return new NewCookie.Builder(REFRESH_COOKIE_NAME)
                .value("")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(false)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }
}