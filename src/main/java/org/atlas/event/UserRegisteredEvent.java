package org.atlas.event;

/**
 * Published on routing key {@code user.registered} after a user is persisted.
 *
 * <p>Consumed by:
 * <ul>
 *   <li>billing-service — creates a default FREE subscription (reads only {@code userId});</li>
 *   <li>mail-service — sends the welcome / email-verification letter (reads
 *       {@code eventId}, {@code email}, {@code name}, {@code verificationToken}).</li>
 * </ul>
 *
 * <p>{@code eventId} is the idempotency key (the same UUID is set as the RabbitMQ messageId).
 */
public record UserRegisteredEvent(
	String eventId,
	String userId,
	String email,
	String name,
	String verificationToken
) {}
