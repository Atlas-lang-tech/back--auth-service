package org.atlas.service;

import org.atlas.dto.AuthDto.LoginRequest;
import org.atlas.dto.AuthDto.RefreshRequest;
import org.atlas.dto.AuthDto.RegisterRequest;
import org.atlas.dto.AuthDto.TokenResponse;
import org.atlas.dto.AuthDto.UserResponse;
import org.atlas.entity.User;
import org.atlas.repository.UserRepository;

import io.quarkus.elytron.security.common.BcryptUtil;
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

		return generateTokenPair(user);
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

	public java.util.List<UserResponse> getAllUsers() {
		return userRepository.listAll().stream()
			.map(this::toUserResponse)
			.toList();
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
			user.role.name()
		);
	}
}
