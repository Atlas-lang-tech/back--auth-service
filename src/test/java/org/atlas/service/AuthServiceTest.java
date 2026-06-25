package org.atlas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.elytron.security.common.BcryptUtil;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.atlas.dto.AuthDto.LoginRequest;
import org.atlas.dto.AuthDto.RefreshRequest;
import org.atlas.dto.AuthDto.RegisterRequest;
import org.atlas.dto.AuthDto.TokenResponse;
import org.atlas.dto.AuthDto.UserResponse;
import org.atlas.entity.User;
import org.atlas.event.PasswordResetRequestedEvent;
import org.atlas.event.UserRegisteredEvent;
import org.atlas.repository.UserRepository;
import org.atlas.test.TestFixtures;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;

/**
 * Чисті юніт-тести бізнес-логіки AuthService на Mockito — без Quarkus/БД/Redis.
 * Поля сервісу package-private, тож виставляємо моки напряму.
 */
class AuthServiceTest {

    AuthService service;
    UserRepository userRepository;
    JwtService jwtService;
    RedisService redisService;
    @SuppressWarnings("unchecked")
    Emitter<UserRegisteredEvent> userRegisteredEmitter = mock(Emitter.class);
    @SuppressWarnings("unchecked")
    Emitter<PasswordResetRequestedEvent> passwordResetEmitter = mock(Emitter.class);

    @BeforeEach
    void setUp() {
        service = new AuthService();
        userRepository = mock(UserRepository.class);
        jwtService = mock(JwtService.class);
        redisService = mock(RedisService.class);
        service.userRepository = userRepository;
        service.jwtService = jwtService;
        service.redisService = redisService;
        service.userRegisteredEmitter = userRegisteredEmitter;
        service.passwordResetEmitter = passwordResetEmitter;

        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpiry()).thenReturn(900L);
        when(jwtService.getRefreshTokenExpiry()).thenReturn(604800L);
    }

    private User persistedUser(String email, User.Role role) {
        User u = TestFixtures.transientUser(UUID.randomUUID(), email, role);
        return u;
    }

    // ---------------------------------------------------------------- register

    @Test
    void register_success_hashesPasswordAndReturnsTokens() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(false);
        // persist призначає id (як зробила б БД)
        doAnswer(inv -> {
            ((User) inv.getArgument(0)).id = UUID.randomUUID();
            return null;
        }).when(userRepository).persist(any(User.class));

        TokenResponse res = service.register(
            new RegisterRequest("a@b.com", "alice", "Password123")
        );

        assertEquals("access-token", res.accessToken());
        assertEquals("refresh-token", res.refreshToken());
        assertEquals(900L, res.expiresIn());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).persist(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertNotEquals("Password123", saved.password, "пароль має бути захешований");
        assertTrue(BcryptUtil.matches("Password123", saved.password));
        verify(redisService).saveRefreshToken(eq(saved.id.toString()), eq("refresh-token"), eq(604800L));

        // Публікуємо user.registered з id нового користувача
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<UserRegisteredEvent>> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(userRegisteredEmitter).send(msgCaptor.capture());
        assertEquals(saved.id.toString(), msgCaptor.getValue().getPayload().userId());
    }

    @Test
    void register_emailInUse_throwsBadRequest() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);
        assertThrows(BadRequestException.class, () ->
            service.register(new RegisterRequest("a@b.com", "alice", "Password123"))
        );
        verify(userRepository, never()).persist(any(User.class));
    }

    // ------------------------------------------------------------------- login

    @Test
    void login_success_returnsTokens() {
        User user = persistedUser("a@b.com", User.Role.USER);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));

        TokenResponse res = service.login(new LoginRequest("a@b.com", "Password123"));

        assertEquals("access-token", res.accessToken());
        verify(redisService).saveRefreshToken(eq(user.id.toString()), eq("refresh-token"), anyLong());
    }

    @Test
    void login_wrongPassword_throwsNotAuthorized() {
        User user = persistedUser("a@b.com", User.Role.USER);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        assertThrows(NotAuthorizedException.class, () ->
            service.login(new LoginRequest("a@b.com", "WrongPassword"))
        );
    }

    @Test
    void login_userNotFound_throwsNotAuthorized() {
        when(userRepository.findByEmail("missing@b.com")).thenReturn(Optional.empty());
        assertThrows(NotAuthorizedException.class, () ->
            service.login(new LoginRequest("missing@b.com", "Password123"))
        );
    }

    // ----------------------------------------------------------------- refresh

    @Test
    void refresh_success_returnsNewTokens() {
        UUID id = UUID.randomUUID();
        String token = TestFixtures.unsignedJwt("{\"sub\":\"" + id + "\"}");
        User user = TestFixtures.transientUser(id, "a@b.com", User.Role.USER);

        when(redisService.isRefreshTokenValid(id.toString(), token)).thenReturn(true);
        when(userRepository.findById(id)).thenReturn(user);

        TokenResponse res = service.refresh(new RefreshRequest(token));
        assertEquals("access-token", res.accessToken());
    }

    @Test
    void refresh_invalidStoredToken_throwsNotAuthorized() {
        UUID id = UUID.randomUUID();
        String token = TestFixtures.unsignedJwt("{\"sub\":\"" + id + "\"}");
        when(redisService.isRefreshTokenValid(id.toString(), token)).thenReturn(false);
        assertThrows(NotAuthorizedException.class, () ->
            service.refresh(new RefreshRequest(token))
        );
    }

    @Test
    void refresh_userDisabled_throwsNotAuthorized() {
        UUID id = UUID.randomUUID();
        String token = TestFixtures.unsignedJwt("{\"sub\":\"" + id + "\"}");
        User user = TestFixtures.transientUser(id, "a@b.com", User.Role.USER);
        user.active = false;
        when(redisService.isRefreshTokenValid(id.toString(), token)).thenReturn(true);
        when(userRepository.findById(id)).thenReturn(user);
        assertThrows(NotAuthorizedException.class, () ->
            service.refresh(new RefreshRequest(token))
        );
    }

    @Test
    void refresh_malformedToken_throwsNotAuthorized() {
        assertThrows(NotAuthorizedException.class, () ->
            service.refresh(new RefreshRequest("not-a-jwt"))
        );
    }

    // ------------------------------------------------------------------ logout

    @Test
    void logout_deletesRefreshToken() {
        service.logout("user-123");
        verify(redisService).deleteRefreshToken("user-123");
    }

    // ----------------------------------------------------------------- profile

    @Test
    void getProfile_found_returnsUser() {
        User user = persistedUser("a@b.com", User.Role.ADMIN);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        UserResponse res = service.getProfile("a@b.com");
        assertEquals("a@b.com", res.email());
        assertEquals("ADMIN", res.role());
    }

    @Test
    void getProfile_notFound_throwsNotFound() {
        when(userRepository.findByEmail("x@b.com")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getProfile("x@b.com"));
    }

    @Test
    void getProfileById_invalidUuid_throwsBadRequest() {
        assertThrows(BadRequestException.class, () -> service.getProfileById("not-uuid"));
    }

    @Test
    void getProfileById_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(null);
        assertThrows(NotFoundException.class, () -> service.getProfileById(id.toString()));
    }

    // -------------------------------------------------------------- updateUser

    @Test
    void updateUser_success_updatesFields() {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "old@b.com", User.Role.USER);
        when(userRepository.findById(id)).thenReturn(user);
        when(userRepository.existsByEmail("new@b.com")).thenReturn(false);

        UserResponse res = service.updateUser(id.toString(), "newname", "new@b.com");
        assertEquals("newname", res.username());
        assertEquals("new@b.com", res.email());
    }

    @Test
    void updateUser_emailTaken_throwsBadRequest() {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "old@b.com", User.Role.USER);
        when(userRepository.findById(id)).thenReturn(user);
        when(userRepository.existsByEmail("taken@b.com")).thenReturn(true);
        assertThrows(BadRequestException.class, () ->
            service.updateUser(id.toString(), "n", "taken@b.com")
        );
    }

    @Test
    void updateUser_sameEmail_skipsUniquenessCheck() {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "same@b.com", User.Role.USER);
        when(userRepository.findById(id)).thenReturn(user);
        // existsByEmail НЕ має викликатись, бо email не змінився
        UserResponse res = service.updateUser(id.toString(), "renamed", "same@b.com");
        assertEquals("renamed", res.username());
        verify(userRepository, never()).existsByEmail(any());
    }

    // ----------------------------------------------------------- updatePassword

    @Test
    void updatePassword_success_rehashesAndInvalidatesSession() {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "a@b.com", User.Role.USER);
        String oldHash = user.password;
        when(userRepository.findById(id)).thenReturn(user);

        service.updatePassword(id.toString(), "Password123", "NewPassword456");

        assertNotEquals(oldHash, user.password);
        assertTrue(BcryptUtil.matches("NewPassword456", user.password));
        verify(redisService).deleteRefreshToken(id.toString());
    }

    @Test
    void updatePassword_wrongCurrent_throwsBadRequest() {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "a@b.com", User.Role.USER);
        when(userRepository.findById(id)).thenReturn(user);
        assertThrows(BadRequestException.class, () ->
            service.updatePassword(id.toString(), "WrongCurrent", "NewPassword456")
        );
        verify(redisService, never()).deleteRefreshToken(any());
    }

    // -------------------------------------------------------------- deleteUser

    @Test
    void deleteUser_removesUserAndSession() {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "a@b.com", User.Role.USER);
        when(userRepository.findById(id)).thenReturn(user);

        service.deleteUser(id.toString());

        verify(redisService).deleteRefreshToken(id.toString());
        verify(userRepository).delete(user);
    }

    // -------------------------------------------------------------- getAllUsers

    @Test
    void getAllUsers_mapsAllToResponses() {
        when(userRepository.listAll()).thenReturn(List.of(
            persistedUser("a@b.com", User.Role.USER),
            persistedUser("b@b.com", User.Role.ADMIN)
        ));
        List<UserResponse> res = service.getAllUsers();
        assertEquals(2, res.size());
    }

    // --------------------------------------------------------------- updateRole

    @Test
    void updateRole_valid_updatesRole() {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "a@b.com", User.Role.USER);
        when(userRepository.findById(id)).thenReturn(user);

        UserResponse res = service.updateRole(id.toString(), "admin");
        assertEquals("ADMIN", res.role());
        assertEquals(User.Role.ADMIN, user.role);
    }

    @Test
    void updateRole_invalid_throwsBadRequest() {
        assertThrows(BadRequestException.class, () ->
            service.updateRole(UUID.randomUUID().toString(), "SUPERUSER")
        );
    }

    // --------------------------------------------------------------- updatePlan

    @Test
    void updatePlan_valid_updatesPlanAndMirrorsToRedis() {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "a@b.com", User.Role.USER);
        when(userRepository.findById(id)).thenReturn(user);

        UserResponse res = service.updatePlan(id.toString(), "pro");
        assertEquals("PRO", res.planCode());
        assertEquals("PRO", user.planCode);
        verify(redisService).setUserPlan(id.toString(), "PRO");
    }

    @Test
    void updatePlan_blank_throwsBadRequest() {
        assertThrows(BadRequestException.class, () ->
            service.updatePlan(UUID.randomUUID().toString(), "  ")
        );
        verify(redisService, never()).setUserPlan(anyString(), anyString());
    }

    // ------------------------------------------------------------ register event

    @Test
    void register_publishesEnrichedEventAndSavesVerificationToken() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(false);
        doAnswer(inv -> {
            ((User) inv.getArgument(0)).id = UUID.randomUUID();
            return null;
        }).when(userRepository).persist(any(User.class));

        service.register(new RegisterRequest("a@b.com", "alice", "Password123"));

        // Токен верифікації збережено в Redis з TTL > 0
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisService).saveVerificationToken(tokenCaptor.capture(), anyString(), anyLong());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<UserRegisteredEvent>> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(userRegisteredEmitter).send(msgCaptor.capture());
        UserRegisteredEvent ev = msgCaptor.getValue().getPayload();
        assertEquals("a@b.com", ev.email());
        assertEquals("alice", ev.name());
        // У подію кладемо саме той токен, що збережений у Redis
        assertEquals(tokenCaptor.getValue(), ev.verificationToken());
        assertEquals(ev.userId(), ev.userId());
    }

    // ----------------------------------------------------------------- verifyEmail

    @Test
    void verifyEmail_validToken_marksVerified() {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "a@b.com", User.Role.USER);
        when(redisService.consumeVerificationToken("vt")).thenReturn(id.toString());
        when(userRepository.findById(id)).thenReturn(user);

        service.verifyEmail("vt");

        assertTrue(user.emailVerified);
    }

    @Test
    void verifyEmail_invalidToken_throwsBadRequest() {
        when(redisService.consumeVerificationToken("bad")).thenReturn(null);
        assertThrows(BadRequestException.class, () -> service.verifyEmail("bad"));
    }

    @Test
    void verifyEmail_userMissing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(redisService.consumeVerificationToken("vt")).thenReturn(id.toString());
        when(userRepository.findById(id)).thenReturn(null);
        assertThrows(NotFoundException.class, () -> service.verifyEmail("vt"));
    }

    // --------------------------------------------------------- requestPasswordReset

    @Test
    void requestPasswordReset_userExists_savesTokenAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "a@b.com", User.Role.USER);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));

        service.requestPasswordReset("a@b.com");

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisService).saveResetToken(tokenCaptor.capture(), eq(id.toString()), anyLong());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<PasswordResetRequestedEvent>> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(passwordResetEmitter).send(msgCaptor.capture());
        PasswordResetRequestedEvent ev = msgCaptor.getValue().getPayload();
        assertEquals("a@b.com", ev.email());
        assertEquals(tokenCaptor.getValue(), ev.resetToken());
    }

    @Test
    void requestPasswordReset_userMissing_isSilentNoOp() {
        when(userRepository.findByEmail("missing@b.com")).thenReturn(Optional.empty());

        service.requestPasswordReset("missing@b.com"); // no throw

        verify(redisService, never()).saveResetToken(anyString(), anyString(), anyLong());
        verify(passwordResetEmitter, never()).send(any(Message.class));
    }

    // ---------------------------------------------------------------- resetPassword

    @Test
    void resetPassword_validToken_rehashesAndInvalidatesSession() {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "a@b.com", User.Role.USER);
        String oldHash = user.password;
        when(redisService.consumeResetToken("rt")).thenReturn(id.toString());
        when(userRepository.findById(id)).thenReturn(user);

        service.resetPassword("rt", "NewPassword456");

        assertNotEquals(oldHash, user.password);
        assertTrue(BcryptUtil.matches("NewPassword456", user.password));
        verify(redisService).deleteRefreshToken(id.toString());
    }

    @Test
    void resetPassword_invalidToken_throwsBadRequest() {
        when(redisService.consumeResetToken("bad")).thenReturn(null);
        assertThrows(BadRequestException.class, () ->
            service.resetPassword("bad", "NewPassword456")
        );
        verify(redisService, never()).deleteRefreshToken(anyString());
    }
}
