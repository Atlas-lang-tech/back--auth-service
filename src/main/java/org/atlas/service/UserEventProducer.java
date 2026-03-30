package org.atlas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

@ApplicationScoped
public class UserEventProducer {

	private static final Logger LOG = Logger.getLogger(UserEventProducer.class);

	@Inject
	@Channel("user-events")
	Emitter<String> emitter;

	@Inject
	ObjectMapper objectMapper;

	public void sendUserRegistered(String userId, String email) {
		sendEvent("USER_REGISTERED", userId, email);
	}

	public void sendUserDeleted(String userId, String email) {
		sendEvent("USER_DELETED", userId, email);
	}

	public void sendUserUpdated(String userId, String email) {
		sendEvent("USER_UPDATED", userId, email);
	}

	private void sendEvent(String type, String userId, String email) {
		try {
			var event = new UserEvent(type, userId, email, System.currentTimeMillis());
			String payload = objectMapper.writeValueAsString(event);
			emitter.send(payload);
			LOG.infof("Sent Kafka event: %s for userId: %s", type, userId);
		} catch (Exception e) {
			LOG.errorf("Failed to send Kafka event: %s", e.getMessage());
		}
	}

	public record UserEvent(String type, String userId, String email, long timestamp) {}
}
