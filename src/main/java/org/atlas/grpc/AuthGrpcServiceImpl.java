package org.atlas.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.UUID;
import org.atlas.entity.User;
import org.atlas.repository.UserRepository;
import org.jboss.logging.Logger;

@GrpcService
public class AuthGrpcServiceImpl implements AuthGrpcService {

	private static final Logger LOG = Logger.getLogger(AuthGrpcServiceImpl.class);

	@Inject
	UserRepository userRepository;

	@Override
	public Uni<ValidateTokenResponse> validateToken(ValidateTokenRequest request) {
		return Uni.createFrom().item(() -> {
			try {
				String token = request.getToken();
				String[] parts = token.split("\\.");
				if (parts.length != 3) {
					return invalidResponse("Invalid token format");
				}

				String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

				var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);

				long exp = node.get("exp").asLong();
				if (System.currentTimeMillis() / 1000 > exp) {
					return invalidResponse("Token expired");
				}

				String userId = node.get("sub").asText();
				String email = node.has("email") ? node.get("email").asText() : "";
				String username = node.has("username") ? node.get("username").asText() : "";
				String role = node.has("groups") ? node.get("groups").get(0).asText() : "USER";

				LOG.debugf("ValidateToken userId=%s", userId);

				return ValidateTokenResponse.newBuilder()
					.setValid(true)
					.setUserId(userId)
					.setEmail(email)
					.setUsername(username)
					.setRole(role)
					.build();
			} catch (Exception e) {
				LOG.error("ValidateToken error", e);
				return invalidResponse(e.getMessage());
			}
		});
	}

	@Override
	public Uni<GetUserResponse> getUser(GetUserRequest request) {
		return Uni.createFrom().item(() -> {
			try {
				UUID userId = UUID.fromString(request.getUserId());
				User user = userRepository.findById(userId);

				if (user == null) {
					return GetUserResponse.newBuilder().setFound(false).build();
				}

				return GetUserResponse.newBuilder()
					.setFound(true)
					.setUserId(user.id.toString())
					.setEmail(user.email)
					.setUsername(user.username)
					.setRole(user.role.name())
					.setActive(user.active)
					.build();
			} catch (Exception e) {
				LOG.error("GetUser error", e);
				return GetUserResponse.newBuilder().setFound(false).build();
			}
		});
	}

	private ValidateTokenResponse invalidResponse(String error) {
		return ValidateTokenResponse.newBuilder().setValid(false).setError(error).build();
	}
}
