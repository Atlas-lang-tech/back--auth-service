package org.atlas.test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Піднімає реальні Postgres + Redis у Docker (Testcontainers) для інтеграційних
 * тестів і віддає Quarkus config-оверрайди з координатами контейнерів.
 *
 * Контейнери статичні — стартують один раз на весь прогін тестів (Quarkus
 * перевикористовує застосунок між тестовими класами з однаковим набором ресурсів).
 */
public class InfraTestResource implements QuarkusTestResourceLifecycleManager {

    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("auth_db")
            .withUsername("auth_user")
            .withPassword("auth_pass");

    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Override
    public Map<String, String> start() {
        POSTGRES.start();
        REDIS.start();

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.db-kind", "postgresql");
        config.put("quarkus.datasource.jdbc.url", POSTGRES.getJdbcUrl());
        config.put("quarkus.datasource.username", POSTGRES.getUsername());
        config.put("quarkus.datasource.password", POSTGRES.getPassword());
        config.put(
            "quarkus.redis.hosts",
            "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379)
        );
        return config;
    }

    @Override
    public void stop() {
        REDIS.stop();
        POSTGRES.stop();
    }
}
