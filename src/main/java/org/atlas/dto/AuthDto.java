package org.atlas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDto {

	public record RegisterRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 3, max = 50) String username,
		@NotBlank @Size(min = 8, max = 100) String password
	) {}

	public record LoginRequest(
		@NotBlank @Email String email,
		@NotBlank String password
	) {}

	public record RefreshRequest(@NotBlank String refreshToken) {}

	public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {}

	public record UserResponse(String id, String email, String username, String role, String planCode) {}

	public record UpdateRoleRequest(@NotBlank String role) {}

	public record UpdatePlanRequest(@NotBlank String planCode) {}

	public record UpdateUserRequest(
		@NotBlank @Size(min = 3, max = 50) String username,
		@NotBlank @Email String email
	) {}

	public record UpdatePasswordRequest(
		@NotBlank String currentPassword,
		@NotBlank @Size(min = 8, max = 100) String newPassword
	) {}

	public record LoginResponse(TokenResponse token, UserResponse user) {}
}
