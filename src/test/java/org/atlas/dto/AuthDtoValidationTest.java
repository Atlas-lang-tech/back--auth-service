package org.atlas.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;
import jakarta.validation.Validator;
import org.atlas.dto.AuthDto.LoginRequest;
import org.atlas.dto.AuthDto.RefreshRequest;
import org.atlas.dto.AuthDto.RegisterRequest;
import org.atlas.dto.AuthDto.UpdatePasswordRequest;
import org.atlas.dto.AuthDto.UpdatePlanRequest;
import org.atlas.dto.AuthDto.UpdateRoleRequest;
import org.atlas.dto.AuthDto.UpdateUserRequest;
import io.quarkus.test.junit.QuarkusTest;
import org.atlas.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/** Перевірка Bean Validation-констрейнтів на DTO-записах. */
@QuarkusTest
class AuthDtoValidationTest extends AbstractIntegrationTest {

    @Inject
    Validator validator;

    @Test
    void registerRequest_valid_passes() {
        assertTrue(validator.validate(
            new RegisterRequest("a@b.com", "alice", "Password123")).isEmpty());
    }

    @Test
    void registerRequest_badEmail_fails() {
        assertFalse(validator.validate(
            new RegisterRequest("not-an-email", "alice", "Password123")).isEmpty());
    }

    @Test
    void registerRequest_shortUsername_fails() {
        assertFalse(validator.validate(
            new RegisterRequest("a@b.com", "ab", "Password123")).isEmpty());
    }

    @Test
    void registerRequest_shortPassword_fails() {
        assertFalse(validator.validate(
            new RegisterRequest("a@b.com", "alice", "short")).isEmpty());
    }

    @Test
    void registerRequest_blankFields_fail() {
        assertFalse(validator.validate(
            new RegisterRequest("", "", "")).isEmpty());
    }

    @Test
    void loginRequest_validation() {
        assertTrue(validator.validate(new LoginRequest("a@b.com", "x")).isEmpty());
        assertFalse(validator.validate(new LoginRequest("bad", "")).isEmpty());
    }

    @Test
    void refreshRequest_blank_fails() {
        assertFalse(validator.validate(new RefreshRequest("")).isEmpty());
        assertTrue(validator.validate(new RefreshRequest("token")).isEmpty());
    }

    @Test
    void updateUserRequest_validation() {
        assertTrue(validator.validate(new UpdateUserRequest("alice", "a@b.com")).isEmpty());
        assertFalse(validator.validate(new UpdateUserRequest("ab", "bad")).isEmpty());
    }

    @Test
    void updatePasswordRequest_validation() {
        assertTrue(validator.validate(
            new UpdatePasswordRequest("oldpass", "NewPassword1")).isEmpty());
        assertFalse(validator.validate(
            new UpdatePasswordRequest("", "short")).isEmpty());
    }

    @Test
    void updateRoleRequest_blank_fails() {
        assertFalse(validator.validate(new UpdateRoleRequest("")).isEmpty());
        assertTrue(validator.validate(new UpdateRoleRequest("ADMIN")).isEmpty());
    }

    @Test
    void updatePlanRequest_blank_fails() {
        assertFalse(validator.validate(new UpdatePlanRequest("")).isEmpty());
        assertTrue(validator.validate(new UpdatePlanRequest("PRO")).isEmpty());
    }
}
