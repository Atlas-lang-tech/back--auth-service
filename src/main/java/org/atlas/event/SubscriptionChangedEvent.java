package org.atlas.event;

/**
 * Consumed from routing key {@code subscription.changed} (published by billing).
 * Carries the new plan for a user so auth can update the stored planCode.
 */
public record SubscriptionChangedEvent(String userId, String planCode) {}
