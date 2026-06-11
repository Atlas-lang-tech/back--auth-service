package org.atlas.test;

import io.quarkus.test.common.QuarkusTestResource;

/**
 * Базовий клас для інтеграційних тестів — реєструє інфра-ресурс
 * ({@link InfraTestResource}: Postgres + Redis) глобально.
 *
 * {@code restrictToAnnotatedClass = false} робить ресурс глобальним, тож контейнери
 * піднімаються один раз на весь модуль. Конкретні тести мають додавати
 * {@code @QuarkusTest} напряму — Quarkus реєструє тест як CDI-бін лише за прямою
 * анотацією, успадкування з базового класу для цього недостатньо.
 */
@QuarkusTestResource(value = InfraTestResource.class, restrictToAnnotatedClass = false)
public abstract class AbstractIntegrationTest {
}
