package org.atlas.service;

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
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;

@ApplicationScoped
public class AuthService {

	@Inject
	UserRepository userRepository;

	@Inject
	JwtService jwtService;

	@Inject
	RedisService redisService;

	@Channel("user-registered")
	Emitter<UserRegisteredEvent> userRegisteredEmitter;

	@Channel("password-reset-requested")
	Emitter<PasswordResetRequestedEvent> passwordResetEmitter;

	// TTL одноразових токенів у Redis
	private static final long VERIFY_TOKEN_TTL = 24 * 60 * 60; // 24 години
	private static final long RESET_TOKEN_TTL = 60 * 60;       // 1 година


	@Transactional
	public TokenResponse register(RegisterRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new BadRequestException("Email already in use");
		}

		User user = new User();
		user.email = request.email();
		user.username = request.username();
		user.password = BcryptUtil.bcryptHash(request.password());
		userRepository.persist(user);

		publishUserRegistered(user);

		return generateTokenPair(user);
	}

	// Announce the new user so billing can provision a default FREE subscription
	// and mail-service can send the welcome / verification letter.
	// The same UUID is the event idempotency key and the RabbitMQ messageId
	// (publish happens before commit, so a rare rollback-after-send is tolerated
	// by an idempotent consumer).
	private void publishUserRegistered(User user) {
		String eventId = UUID.randomUUID().toString();
		String verificationToken = UUID.randomUUID().toString();
		// Токен дійсний один раз протягом VERIFY_TOKEN_TTL — далі POST /verify-email.
		redisService.saveVerificationToken(verificationToken, user.id.toString(), VERIFY_TOKEN_TTL);

		OutgoingRabbitMQMetadata metadata = new OutgoingRabbitMQMetadata.Builder()
			.withMessageId(eventId)
			.build();
		userRegisteredEmitter.send(
			Message.of(new UserRegisteredEvent(
				eventId,
				user.id.toString(),
				user.email,
				user.username,
				verificationToken
			)).addMetadata(metadata));
	}

	// Підтвердження email за одноразовим токеном. М'яка верифікація: вхід не
	// блокується, прапорець потрібен лише для UI.
	@Transactional
	public void verifyEmail(String token) {
		String userId = redisService.consumeVerificationToken(token);
		if (userId == null) {
			throw new BadRequestException("Invalid or expired verification token");
		}
		User user = userRepository.findById(UUID.fromString(userId));
		if (user == null) {
			throw new jakarta.ws.rs.NotFoundException("User not found");
		}
		user.emailVerified = true;
	}

	// Ініціація скидання пароля. Завжди завершується тихо (без розкриття, чи існує
	// email) — лист надсилається лише якщо користувач знайдений.
	public void requestPasswordReset(String email) {
		userRepository.findByEmail(email).ifPresent(user -> {
			String eventId = UUID.randomUUID().toString();
			String resetToken = UUID.randomUUID().toString();
			redisService.saveResetToken(resetToken, user.id.toString(), RESET_TOKEN_TTL);

			OutgoingRabbitMQMetadata metadata = new OutgoingRabbitMQMetadata.Builder()
				.withMessageId(eventId)
				.build();
			passwordResetEmitter.send(
				Message.of(new PasswordResetRequestedEvent(
					eventId,
					user.id.toString(),
					user.email,
					resetToken
				)).addMetadata(metadata));
		});
	}

	// Завершення скидання пароля за одноразовим токеном.
	@Transactional
	public void resetPassword(String token, String newPassword) {
		String userId = redisService.consumeResetToken(token);
		if (userId == null) {
			throw new BadRequestException("Invalid or expired reset token");
		}
		User user = userRepository.findById(UUID.fromString(userId));
		if (user == null) {
			throw new jakarta.ws.rs.NotFoundException("User not found");
		}
		user.password = BcryptUtil.bcryptHash(newPassword);
		// Скидання пароля інвалідовує активну сесію.
		redisService.deleteRefreshToken(userId);
	}

	public TokenResponse login(LoginRequest request) {
		User user = userRepository
			.findByEmail(request.email())
			.orElseThrow(() -> new NotAuthorizedException("Invalid credentials"));

		if (!BcryptUtil.matches(request.password(), user.password)) {
			throw new NotAuthorizedException("Invalid credentials");
		}

		return generateTokenPair(user);
	}

	public TokenResponse refresh(RefreshRequest request) {
		String userId = extractUserIdFromRefreshToken(request.refreshToken());

		if (!redisService.isRefreshTokenValid(userId, request.refreshToken())) {
			throw new NotAuthorizedException("Invalid or expired refresh token");
		}

		User user = userRepository.findById(java.util.UUID.fromString(userId));
		if (user == null || !user.active) {
			throw new NotAuthorizedException("User not found or disabled");
		}

		return generateTokenPair(user);
	}

	public void logout(String userId) {
		redisService.deleteRefreshToken(userId);
	}

	public UserResponse getProfile(String email) {
		User user = userRepository.findByEmail(email).orElse(null);
		if (user == null) throw new jakarta.ws.rs.NotFoundException("User not found");
		return toUserResponse(user);
	}

	public UserResponse getProfileById(String userId) {
		return toUserResponse(findUserOr404(userId));
	}

	@Transactional
	public UserResponse updateUser(String userId, String username, String email) {
		User user = findUserOr404(userId);

		// Email міняється — переконуємось, що він ще ніким не зайнятий
		if (!user.email.equalsIgnoreCase(email) && userRepository.existsByEmail(email)) {
			throw new BadRequestException("Email already in use");
		}

		user.username = username;
		user.email = email;
		return toUserResponse(user);
	}

	@Transactional
	public void updatePassword(String userId, String currentPassword, String newPassword) {
		User user = findUserOr404(userId);

		if (!BcryptUtil.matches(currentPassword, user.password)) {
			throw new BadRequestException("Current password is incorrect");
		}

		user.password = BcryptUtil.bcryptHash(newPassword);
		// Пароль змінився — інвалідовуємо активну сесію (refresh-токен)
		redisService.deleteRefreshToken(userId);
	}

	@Transactional
	public void deleteUser(String userId) {
		User user = findUserOr404(userId);
		redisService.deleteRefreshToken(userId);
		userRepository.delete(user);
	}

	public java.util.List<UserResponse> getAllUsers() {
		return userRepository.listAll().stream()
			.map(this::toUserResponse)
			.toList();
	}

	@Transactional
	public UserResponse updateRole(String userId, String role) {
		User.Role newRole;
		try {
			newRole = User.Role.valueOf(role.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new BadRequestException("Invalid role: " + role);
		}

		User user = findUserOr404(userId);
		user.role = newRole;
		return toUserResponse(user);
	}

	@Transactional
	public UserResponse updatePlan(String userId, String planCode) {
		if (planCode == null || planCode.isBlank()) {
			throw new BadRequestException("Plan code must not be blank");
		}
		String normalized = planCode.trim().toUpperCase();

		User user = findUserOr404(userId);
		user.planCode = normalized;
		// Живий mirror у Redis — щоб ForwardAuth віддавав новий план уже на
		// наступному /verify, не чекаючи рефрешу access-токена (як це робить
		// споживач subscription.changed).
		redisService.setUserPlan(userId, normalized);
		return toUserResponse(user);
	}

	// Парсить UUID і дістає користувача або кидає 400/404
	private User findUserOr404(String userId) {
		java.util.UUID id;
		try {
			id = java.util.UUID.fromString(userId);
		} catch (IllegalArgumentException e) {
			throw new BadRequestException("Invalid user id: " + userId);
		}

		User user = userRepository.findById(id);
		if (user == null) {
			throw new jakarta.ws.rs.NotFoundException("User not found");
		}
		return user;
	}

	private TokenResponse generateTokenPair(User user) {
		String accessToken = jwtService.generateAccessToken(user);
		String refreshToken = jwtService.generateRefreshToken(user);

		redisService.saveRefreshToken(
			user.id.toString(),
			refreshToken,
			jwtService.getRefreshTokenExpiry()
		);

		return new TokenResponse(
			accessToken,
			refreshToken,
			jwtService.getAccessTokenExpiry()
		);
	}

	private String extractUserIdFromRefreshToken(String token) {
		try {
			String[] parts = token.split("\\.");
			String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
			var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
			return node.get("sub").asText();
		} catch (Exception e) {
			throw new NotAuthorizedException("Invalid refresh token format");
		}
	}

	private UserResponse toUserResponse(User user) {
		return new UserResponse(
			user.id.toString(),
			user.email,
			user.username,
			user.role.name(),
			user.planCode,
			user.emailVerified
		);
	}
}
