package org.atlas.service;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

import org.atlas.entity.User;
import org.atlas.repository.UserRepository;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.reactive.messaging.rabbitmq.IncomingRabbitMQMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Слухає {@code subscription.changed} (публікує billing) і оновлює planCode
 * користувача. Споживання ідемпотентне — дедуп за RabbitMQ messageId через Redis.
 *
 * Кеш рішень ForwardAuth тут НЕ інвалідовується свідомо: план зашитий у JWT-claim,
 * а TTL кешу ≤60с, тож новий план приїде природно на наступному refresh токена.
 */
@ApplicationScoped
public class SubscriptionEventConsumer {

	private static final Logger LOG = Logger.getLogger(SubscriptionEventConsumer.class);

	// Дедуп-запис живе достатньо довго, щоб перекрити будь-який retry/redelivery.
	private static final long DEDUP_TTL_SECONDS = 24 * 60 * 60;

	@Inject
	UserRepository userRepository;

	@Inject
	RedisService redisService;

	@Incoming("subscription-changed")
	@Blocking
	@Transactional
	public CompletionStage<Void> onSubscriptionChanged(Message<JsonObject> message) {
		JsonObject body = message.getPayload();
		String messageId = message.getMetadata(IncomingRabbitMQMetadata.class)
			.flatMap(IncomingRabbitMQMetadata::getMessageId)
			.orElse(null);

		applySubscriptionChange(messageId, body.getString("userId"), body.getString("planCode"));
		return message.ack();
	}

	// Виділено окремо від messaging-glue, щоб логіку можна було юніт-тестувати без брокера.
	void applySubscriptionChange(String messageId, String userId, String planCode) {
		if (messageId != null && redisService.isEventProcessed(messageId)) {
			LOG.debugf("subscription.changed %s вже оброблено — пропускаємо", messageId);
			return;
		}

		if (userId == null || planCode == null) {
			LOG.warnf("subscription.changed без userId/planCode (messageId=%s) — ігноруємо", messageId);
			return;
		}

		User user = userRepository.findById(UUID.fromString(userId));
		if (user == null) {
			// Юзера нема (видалений / ще не зреплікований) — ack, щоб не зациклити чергу.
			LOG.warnf("subscription.changed для невідомого userId=%s — ігноруємо", userId);
			return;
		}

		user.planCode = planCode;

		if (messageId != null) {
			redisService.markEventProcessed(messageId, DEDUP_TTL_SECONDS);
		}
	}
}
