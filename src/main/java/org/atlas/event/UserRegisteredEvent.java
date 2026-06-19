package org.atlas.event;

/**
 * Published on routing key {@code user.registered} after a user is persisted.
 * Consumed by the billing service to create a default FREE subscription.
 */
public record UserRegisteredEvent(String userId) {}
