package org.atlas.test;

import io.quarkus.elytron.security.common.BcryptUtil;
import java.util.Base64;
import java.util.UUID;
import org.atlas.entity.User;

/** Спільні хелпери для тестів. */
public final class TestFixtures {

    public static final String DEFAULT_PASSWORD = "Password123";

    private TestFixtures() {}

    /** Унікальний email, щоб тести не конфліктували за unique-констрейнтом. */
    public static String randomEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /** Транзієнтний користувач із заданим id (для мінтингу токенів без БД). */
    public static User transientUser(UUID id, String email, User.Role role) {
        User u = new User();
        u.id = id;
        u.email = email;
        u.username = "tester";
        u.password = BcryptUtil.bcryptHash(DEFAULT_PASSWORD);
        u.role = role;
        u.active = true;
        return u;
    }

    /** Користувач для персисту (id призначає Hibernate). */
    public static User newUser(String email, User.Role role) {
        User u = new User();
        u.email = email;
        u.username = "tester";
        u.password = BcryptUtil.bcryptHash(DEFAULT_PASSWORD);
        u.role = role;
        u.active = true;
        return u;
    }

    /** Будує неперевірений JWT-рядок "header.payload.sig" із заданим payload-JSON. */
    public static String unsignedJwt(String payloadJson) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = enc.encodeToString(payloadJson.getBytes());
        return header + "." + payload + ".sig";
    }
}
