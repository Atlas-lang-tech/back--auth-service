package org.atlas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.UUID;

import org.atlas.entity.User;
import org.atlas.repository.UserRepository;
import org.atlas.test.AbstractIntegrationTest;
import org.atlas.test.TestFixtures;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

/**
 * Інтеграційний тест: повідомлення subscription.changed, що приходить in-memory
 * каналом, оновлює planCode користувача в реальному Postgres.
 */
@QuarkusTest
class SubscriptionEventConsumerChannelTest extends AbstractIntegrationTest {

    @Inject
    UserRepository userRepository;

    @Inject
    @Any
    InMemoryConnector connector;

    @Test
    void subscriptionChanged_updatesPlanCode() {
        UUID id = QuarkusTransaction.requiringNew().call(() -> {
            User user = TestFixtures.newUser(TestFixtures.randomEmail(), User.Role.USER);
            userRepository.persist(user);
            return user.id;
        });

        InMemorySource<JsonObject> source = connector.source("subscription-changed");
        source.send(new JsonObject().put("userId", id.toString()).put("planCode", "PRO"));

        awaitPlanCode(id, "PRO");
    }

    // Споживач працює на воркер-треді (@Blocking) — поллимо БД до оновлення.
    private void awaitPlanCode(UUID id, String expected) {
        for (int i = 0; i < 50; i++) {
            String plan = QuarkusTransaction.requiringNew()
                .call(() -> userRepository.findById(id).planCode);
            if (expected.equals(plan)) {
                assertEquals(expected, plan);
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("перервано під час очікування planCode");
            }
        }
        fail("planCode не оновився до '" + expected + "' за відведений час");
    }
}
