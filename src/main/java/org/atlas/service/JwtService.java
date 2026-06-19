package org.atlas.service;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import org.atlas.entity.User;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

@ApplicationScoped
public class JwtService {

	@Inject
	JsonWebToken jwt;

	@ConfigProperty(name = "mp.jwt.verify.issuer")
	String issuer;

	@ConfigProperty(name = "atlas.jwt.access-token.expiry", defaultValue = "900")
	long accessTokenExpiry;

	@ConfigProperty(name = "atlas.jwt.refresh-token.expiry", defaultValue = "604800")
	long refreshTokenExpiry;

	public String generateAccessToken(User user) {
		return Jwt.issuer(issuer)
			.subject(user.id.toString())
			.groups(user.role.name())
			.claim("email", user.email)
			.claim("id", user.id)
			.claim("plan", user.planCode)
			.expiresAt(Instant.now().plusSeconds(accessTokenExpiry))
			.sign();
	}

	public String generateRefreshToken(User user) {
		return Jwt.issuer(issuer)
			.subject(user.id.toString())
			.claim("type", "refresh")
			.claim("jti", UUID.randomUUID().toString())
			.expiresAt(Instant.now().plusSeconds(refreshTokenExpiry))
			.sign();
	}

	public String getSubject() {
		return jwt.getSubject();
	}

	public String getEmail() {
		return jwt.getClaim("email");
	}

	public boolean isRefreshToken(JsonWebToken token) {
		String type = token.getClaim("type");
		return "refresh".equals(type);
	}

	public long getAccessTokenExpiry() {
		return accessTokenExpiry;
	}

	public long getRefreshTokenExpiry() {
		return refreshTokenExpiry;
	}
}
