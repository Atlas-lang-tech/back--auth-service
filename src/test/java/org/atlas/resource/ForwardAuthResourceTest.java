package org.atlas.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import io.quarkus.test.junit.QuarkusTest;
import org.atlas.test.AbstractIntegrationTest;
import org.atlas.test.TestFixtures;
import org.junit.jupiter.api.Test;

/** Тести ендпоінта /verify для Traefik ForwardAuth. */
@QuarkusTest
class ForwardAuthResourceTest extends AbstractIntegrationTest {

    private String registerAndGetAccessToken() {
        Response r = given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", TestFixtures.randomEmail(), "username", "alice", "password", "Password123"))
            .when().post("/api/auth/register");
        r.then().statusCode(201);
        return r.jsonPath().getString("token.accessToken");
    }

    @Test
    void verify_validToken_returns200WithUserHeaders() {
        String token = registerAndGetAccessToken();
        given()
            .header("Authorization", "Bearer " + token)
            .header("X-Forwarded-Uri", "/api/protected")
            .header("X-Forwarded-Method", "GET")
            .when().get("/verify")
            .then()
            .statusCode(200)
            .header("X-User-Id", notNullValue())
            .header("X-User-Role", notNullValue());
    }

    @Test
    void verify_noToken_returns401() {
        given()
            .header("X-Forwarded-Uri", "/api/protected")
            .header("X-Forwarded-Method", "GET")
            .when().get("/verify")
            .then().statusCode(401);
    }

    @Test
    void verify_invalidToken_returns401() {
        given()
            .header("Authorization", "Bearer not-a-real-token")
            .header("X-Forwarded-Uri", "/api/protected")
            .header("X-Forwarded-Method", "GET")
            .when().get("/verify")
            .then().statusCode(401);
    }
}
