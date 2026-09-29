
package com.capstone.transaction.kafka;

import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.event.TransactionCompletedEvent;
import com.capstone.common.event.TransactionCreatedEvent;
import com.capstone.common.event.TransactionFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Partition key is accountId (String UUID) so all events for the same
 * account land on the same partition and maintain ordering.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishCreated(TransactionCreatedEvent event) {
        publish(event.accountId(), event);
    }

    public void publishCompleted(TransactionCompletedEvent event) {
        publish(event.accountId(), event);
    }

    public void publishFailed(TransactionFailedEvent event) {
        publish(event.accountId(), event);
    }

    public void publishRaw(String key, Object event) {
        publish(key, event);
    }

    public void publishToTopic(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payload={} to topic={}", event, topic, ex, "\n");
                    } else {
                        log.info("Published payload={} to topic={} partition={}\n",
                                event, topic, result.getRecordMetadata().partition());
                    }
                });
    }

    private void publish(String key, Object event) {
        kafkaTemplate.send(KafkaTopics.TRANSACTION_EVENTS, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payload={} to topic={}",
                                event, KafkaTopics.TRANSACTION_EVENTS, ex, "\n");
                    } else {
                        log.info("Published payload={} to topic={} partition={}\n",
                                event, KafkaTopics.TRANSACTION_EVENTS,
                                result.getRecordMetadata().partition());
                    }
                });
    }
}

