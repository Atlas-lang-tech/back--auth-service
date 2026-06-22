package org.atlas.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.atlas.entity.User;
import org.atlas.repository.UserRepository;
import org.atlas.test.AbstractIntegrationTest;
import org.atlas.test.TestFixtures;
import org.junit.jupiter.api.Test;

/** End-to-end тести REST-ендпоінтів AuthResource через HTTP (RestAssured). */
@QuarkusTest
class AuthResourceTest extends AbstractIntegrationTest {

    @Inject
    UserRepository userRepository;

    /** Реєструє нового користувача й повертає його дані разом з токенами. */
    private Cred register() {
        String email = TestFixtures.randomEmail();
        Response r = given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", email, "username", "alice", "password", "Password123"))
            .when().post("/api/auth/register");
        r.then().statusCode(201);
        return new Cred(
            email,
            r.jsonPath().getString("user.id"),
            r.jsonPath().getString("token.accessToken"),
            r.jsonPath().getString("token.refreshToken")
        );
    }

    record Cred(String email, String id, String accessToken, String refreshToken) {}

    /**
     * Registers a user, promotes them to ADMIN in the DB and re-logs in so the
     * returned access token carries the ADMIN group (used to exercise
     * `@RolesAllowed("ADMIN")` endpoints).
     */
    private Cred registerAdmin() {
        Cred c = register();
        QuarkusTransaction.requiringNew().run(() -> {
            User u = userRepository.findById(UUID.fromString(c.id()));
            u.role = User.Role.ADMIN;
        });
        Response r = given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", c.email(), "password", "Password123"))
            .when().post("/api/auth/login");
        r.then().statusCode(200);
        return new Cred(
            c.email(),
            c.id(),
            r.jsonPath().getString("token.accessToken"),
            r.jsonPath().getString("token.refreshToken")
        );
    }

    // ----------------------------------------------------------------- register

    @Test
    void register_returns201WithTokensAndCookie() {
        String email = TestFixtures.randomEmail();
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", email, "username", "alice", "password", "Password123"))
            .when().post("/api/auth/register")
            .then()
            .statusCode(201)
            .cookie("refreshToken", notNullValue())
            .body("token.accessToken", notNullValue())
            .body("user.email", equalTo(email))
            .body("user.role", equalTo("USER"));
    }

    @Test
    void register_duplicateEmail_returns400() {
        Cred c = register();
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", c.email(), "username", "bob", "password", "Password123"))
            .when().post("/api/auth/register")
            .then().statusCode(400);
    }

    @Test
    void register_invalidEmail_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", "not-an-email", "username", "alice", "password", "Password123"))
            .when().post("/api/auth/register")
            .then().statusCode(400);
    }

    @Test
    void register_shortPassword_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", TestFixtures.randomEmail(), "username", "alice", "password", "short"))
            .when().post("/api/auth/register")
            .then().statusCode(400);
    }

    // -------------------------------------------------------------------- login

    @Test
    void login_success_returns200() {
        Cred c = register();
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", c.email(), "password", "Password123"))
            .when().post("/api/auth/login")
            .then()
            .statusCode(200)
            .body("token.accessToken", notNullValue())
            .body("user.email", equalTo(c.email()));
    }

    @Test
    void login_wrongPassword_returns401() {
        Cred c = register();
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", c.email(), "password", "WrongPassword"))
            .when().post("/api/auth/login")
            .then().statusCode(401);
    }

    // -------------------------------------------------------------- protected

    @Test
    void getCurrentUser_withToken_returnsUser() {
        Cred c = register();
        given()
            .header("Authorization", "Bearer " + c.accessToken())
            .when().get("/api/auth/user/me")
            .then()
            .statusCode(200)
            .body("id", equalTo(c.id()))
            .body("email", equalTo(c.email()));
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() {
        given()
            .when().get("/api/auth/user/me")
            .then().statusCode(401);
    }

    @Test
    void getAllUsers_asAdmin_returns200() {
        Cred admin = registerAdmin();
        given()
            .header("Authorization", "Bearer " + admin.accessToken())
            .when().get("/api/auth/users")
            .then()
            .statusCode(200)
            .body("$", notNullValue());
    }

    @Test
    void getAllUsers_asNonAdmin_returns403() {
        Cred c = register();
        given()
            .header("Authorization", "Bearer " + c.accessToken())
            .when().get("/api/auth/users")
            .then().statusCode(403);
    }

    @Test
    void updateUser_withToken_updatesProfile() {
        Cred c = register();
        String newEmail = TestFixtures.randomEmail();
        given()
            .header("Authorization", "Bearer " + c.accessToken())
            .contentType(ContentType.JSON)
            .body(Map.of("username", "alice2", "email", newEmail))
            .when().patch("/api/auth/user/" + c.id())
            .then()
            .statusCode(200)
            .body("username", equalTo("alice2"))
            .body("email", equalTo(newEmail));
    }

    @Test
    void updateRole_asAdmin_changesRole() {
        Cred admin = registerAdmin();
        Cred victim = register();
        given()
            .header("Authorization", "Bearer " + admin.accessToken())
            .contentType(ContentType.JSON)
            .body(Map.of("role", "ADMIN"))
            .when().patch("/api/auth/user/" + victim.id() + "/role")
            .then()
            .statusCode(200)
            .body("role", equalTo("ADMIN"));
    }

    @Test
    void updateRole_asNonAdmin_returns403() {
        Cred c = register();
        given()
            .header("Authorization", "Bearer " + c.accessToken())
            .contentType(ContentType.JSON)
            .body(Map.of("role", "ADMIN"))
            .when().patch("/api/auth/user/" + c.id() + "/role")
            .then().statusCode(403);
    }

    @Test
    void updateRole_invalidRole_returns400() {
        Cred admin = registerAdmin();
        given()
            .header("Authorization", "Bearer " + admin.accessToken())
            .contentType(ContentType.JSON)
            .body(Map.of("role", "SUPERUSER"))
            .when().patch("/api/auth/user/" + admin.id() + "/role")
            .then().statusCode(400);
    }

    @Test
    void updatePlan_asAdmin_changesPlan() {
        Cred admin = registerAdmin();
        Cred victim = register();
        given()
            .header("Authorization", "Bearer " + admin.accessToken())
            .contentType(ContentType.JSON)
            .body(Map.of("planCode", "pro"))
            .when().patch("/api/auth/user/" + victim.id() + "/plan")
            .then()
            .statusCode(200)
            .body("planCode", equalTo("PRO"));
    }

    @Test
    void updatePlan_asNonAdmin_returns403() {
        Cred c = register();
        given()
            .header("Authorization", "Bearer " + c.accessToken())
            .contentType(ContentType.JSON)
            .body(Map.of("planCode", "PRO"))
            .when().patch("/api/auth/user/" + c.id() + "/plan")
            .then().statusCode(403);
    }

    @Test
    void updatePlan_blankPlan_returns400() {
        Cred admin = registerAdmin();
        given()
            .header("Authorization", "Bearer " + admin.accessToken())
            .contentType(ContentType.JSON)
            .body(Map.of("planCode", ""))
            .when().patch("/api/auth/user/" + admin.id() + "/plan")
            .then().statusCode(400);
    }

    @Test
    void updatePassword_withToken_returns204() {
        Cred c = register();
        given()
            .header("Authorization", "Bearer " + c.accessToken())
            .contentType(ContentType.JSON)
            .body(Map.of("currentPassword", "Password123", "newPassword", "NewPassword456"))
            .when().patch("/api/auth/user/" + c.id() + "/password")
            .then().statusCode(204);
    }

    // ------------------------------------------------------------------ refresh

    @Test
    void refresh_withValidToken_returns200() {
        Cred c = register();
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("refreshToken", c.refreshToken()))
            .when().post("/api/auth/refresh")
            .then()
            .statusCode(200)
            .body("accessToken", notNullValue());
    }

    // ------------------------------------------------------------ logout/delete

    @Test
    void logout_withToken_returns204() {
        Cred c = register();
        given()
            .header("Authorization", "Bearer " + c.accessToken())
            .contentType(ContentType.JSON)
            .when().post("/api/auth/logout")
            .then().statusCode(204);
    }

    @Test
    void deleteUser_withToken_returns204() {
        Cred c = register();
        given()
            .header("Authorization", "Bearer " + c.accessToken())
            .when().delete("/api/auth/user/" + c.id())
            .then().statusCode(204);
    }
}
