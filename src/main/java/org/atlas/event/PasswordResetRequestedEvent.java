package org.atlas.event;

/**
 * Published on routing key {@code password.reset_requested} when a user asks to
 * reset their password. Consumed by mail-service, which emails the reset link
 * built from {@code resetToken}.
 *
 * <p>{@code eventId} is the idempotency key (the same UUID is set as the RabbitMQ messageId).
 */
public record PasswordResetRequestedEvent(
	String eventId,
	String userId,
	String email,
	String resetToken
) {}
