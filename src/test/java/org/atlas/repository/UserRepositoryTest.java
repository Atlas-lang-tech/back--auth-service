package org.atlas.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.atlas.entity.User;
import io.quarkus.test.junit.QuarkusTest;
import org.atlas.test.AbstractIntegrationTest;
import org.atlas.test.TestFixtures;
import org.junit.jupiter.api.Test;

/** Інтеграційні тести UserRepository проти реального Postgres + Flyway-схеми. */
@QuarkusTest
class UserRepositoryTest extends AbstractIntegrationTest {

    @Inject
    UserRepository repository;

    @Test
    @Transactional
    void persistAndFindByEmail() {
        String email = TestFixtures.randomEmail();
        User user = TestFixtures.newUser(email, User.Role.USER);
        repository.persist(user);

        assertNotNull(user.id, "Hibernate має призначити UUID");
        assertTrue(repository.findByEmail(email).isPresent());
        assertEquals(email, repository.findByEmail(email).get().email);
    }

    @Test
    void findByEmail_absent_returnsEmpty() {
        assertTrue(repository.findByEmail("no-such-" + TestFixtures.randomEmail()).isEmpty());
    }

    @Test
    @Transactional
    void existsByEmail_reflectsPersistence() {
        String email = TestFixtures.randomEmail();
        assertFalse(repository.existsByEmail(email));
        repository.persist(TestFixtures.newUser(email, User.Role.USER));
        assertTrue(repository.existsByEmail(email));
    }

    @Test
    @Transactional
    void findById_returnsPersistedUser() {
        User user = TestFixtures.newUser(TestFixtures.randomEmail(), User.Role.MODERATOR);
        repository.persist(user);
        User found = repository.findById(user.id);
        assertNotNull(found);
        assertEquals(User.Role.MODERATOR, found.role);
    }
}
