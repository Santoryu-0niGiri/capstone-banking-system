package com.capstone.notification.kafka;

import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.event.TransactionCompletedEvent;
import com.capstone.common.event.TransactionCreatedEvent;
import com.capstone.common.event.TransactionFailedEvent;
import com.capstone.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single listener on `transaction-events` that fans an incoming payload out
 * to the right handler based on its shape, since TransactionCreatedEvent,
 * TransactionCompletedEvent and TransactionFailedEvent are all published to
 * the same topic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.TRANSACTION_EVENTS, containerFactory = "kafkaListenerContainerFactory")
    public void onMessage(Object payload) {
        Map<?, ?> asMap = objectMapper.convertValue(payload, LinkedHashMap.class);
        try {
            if (asMap.containsKey("reason")) {
                notificationService.notifyFailed(objectMapper.convertValue(asMap, TransactionFailedEvent.class));
            } else if (asMap.containsKey("balanceAfter")) {
                notificationService.notifyCompleted(objectMapper.convertValue(asMap, TransactionCompletedEvent.class));
            } else {
                notificationService.notifyCreated(objectMapper.convertValue(asMap, TransactionCreatedEvent.class));
            }
        } catch (IllegalArgumentException e) {
            log.error("Failed to deserialize transaction event payload: {}", payload, e);
        }
    }
}
