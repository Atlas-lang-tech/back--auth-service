package org.atlas.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import java.util.UUID;
import org.atlas.entity.User;
import org.atlas.repository.UserRepository;
import org.atlas.service.JwtService;
import io.quarkus.test.junit.QuarkusTest;
import org.atlas.test.AbstractIntegrationTest;
import org.atlas.test.TestFixtures;
import org.junit.jupiter.api.Test;

/** Інтеграційні тести gRPC-сервісу через реального клієнта. */
@QuarkusTest
class AuthGrpcServiceImplTest extends AbstractIntegrationTest {

    @GrpcClient("auth")
    AuthGrpcService client;

    @Inject
    JwtService jwtService;

    @Inject
    UserRepository userRepository;

    // --------------------------------------------------------- validateToken

    @Test
    void validateToken_valid_returnsClaims() {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "grpc@example.com", User.Role.USER);
        String token = jwtService.generateAccessToken(user);

        ValidateTokenResponse res = client
            .validateToken(ValidateTokenRequest.newBuilder().setToken(token).build())
            .await().indefinitely();

        assertTrue(res.getValid());
        assertEquals(id.toString(), res.getUserId());
        assertEquals("grpc@example.com", res.getEmail());
        assertEquals("USER", res.getRole());
    }

    @Test
    void validateToken_expired_returnsInvalid() {
        String payload = "{\"exp\":1000000000,\"sub\":\"" + UUID.randomUUID()
            + "\",\"groups\":[\"USER\"],\"email\":\"x@y.com\"}";
        String token = TestFixtures.unsignedJwt(payload);

        ValidateTokenResponse res = client
            .validateToken(ValidateTokenRequest.newBuilder().setToken(token).build())
            .await().indefinitely();

        assertFalse(res.getValid());
        assertEquals("Token expired", res.getError());
    }

    @Test
    void validateToken_malformed_returnsInvalid() {
        ValidateTokenResponse res = client
            .validateToken(ValidateTokenRequest.newBuilder().setToken("abc").build())
            .await().indefinitely();
        assertFalse(res.getValid());
        assertEquals("Invalid token format", res.getError());
    }

    // ----------------------------------------------------------------- getUser

    @Test
    void getUser_found_returnsUser() {
        User user = TestFixtures.newUser(TestFixtures.randomEmail(), User.Role.ADMIN);
        UUID id = QuarkusTransaction.requiringNew().call(() -> {
            userRepository.persist(user);
            return user.id;
        });

        GetUserResponse res = client
            .getUser(GetUserRequest.newBuilder().setUserId(id.toString()).build())
            .await().indefinitely();

        assertTrue(res.getFound());
        assertEquals(id.toString(), res.getUserId());
        assertEquals("ADMIN", res.getRole());
        assertTrue(res.getActive());
    }

    @Test
    void getUser_notFound_returnsFoundFalse() {
        GetUserResponse res = client
            .getUser(GetUserRequest.newBuilder().setUserId(UUID.randomUUID().toString()).build())
            .await().indefinitely();
        assertFalse(res.getFound());
    }

    @Test
    void getUser_invalidUuid_returnsFoundFalse() {
        GetUserResponse res = client
            .getUser(GetUserRequest.newBuilder().setUserId("not-a-uuid").build())
            .await().indefinitely();
        assertFalse(res.getFound());
    }
}
