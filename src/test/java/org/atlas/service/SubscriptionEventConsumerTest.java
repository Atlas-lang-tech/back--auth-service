package org.atlas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.atlas.entity.User;
import org.atlas.repository.UserRepository;
import org.atlas.test.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Юніт-тести логіки споживання subscription.changed на Mockito — без брокера/БД. */
class SubscriptionEventConsumerTest {

    SubscriptionEventConsumer consumer;
    UserRepository userRepository;
    RedisService redisService;

    @BeforeEach
    void setUp() {
        consumer = new SubscriptionEventConsumer();
        userRepository = mock(UserRepository.class);
        redisService = mock(RedisService.class);
        consumer.userRepository = userRepository;
        consumer.redisService = redisService;
    }

    @Test
    void applies_updatesPlanCode_andMarksProcessed() {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "a@b.com", User.Role.USER);
        when(userRepository.findById(id)).thenReturn(user);
        when(redisService.isEventProcessed("msg-1")).thenReturn(false);

        consumer.applySubscriptionChange("msg-1", id.toString(), "PRO");

        assertEquals("PRO", user.planCode);
        verify(redisService).markEventProcessed(eq("msg-1"), anyLong());
    }

    @Test
    void deduplicates_byMessageId() {
        when(redisService.isEventProcessed("msg-dup")).thenReturn(true);

        consumer.applySubscriptionChange("msg-dup", UUID.randomUUID().toString(), "PRO");

        verify(userRepository, never()).findById(any());
        verify(redisService, never()).markEventProcessed(anyString(), anyLong());
    }

    @Test
    void unknownUser_isIgnored_notMarkedProcessed() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(null);

        consumer.applySubscriptionChange("msg-2", id.toString(), "PRO");

        verify(userRepository).findById(id);
        verify(redisService, never()).markEventProcessed(anyString(), anyLong());
    }

    @Test
    void missingFields_areIgnored() {
        consumer.applySubscriptionChange("msg-3", null, "PRO");
        consumer.applySubscriptionChange("msg-4", UUID.randomUUID().toString(), null);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void nullMessageId_stillUpdates_withoutDedup() {
        UUID id = UUID.randomUUID();
        User user = TestFixtures.transientUser(id, "a@b.com", User.Role.USER);
        when(userRepository.findById(id)).thenReturn(user);

        consumer.applySubscriptionChange(null, id.toString(), "PREMIUM");

        assertEquals("PREMIUM", user.planCode);
        verify(redisService, never()).isEventProcessed(anyString());
        verify(redisService, never()).markEventProcessed(anyString(), anyLong());
    }
}
