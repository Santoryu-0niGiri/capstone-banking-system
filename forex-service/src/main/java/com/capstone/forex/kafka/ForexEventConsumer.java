package com.capstone.forex.kafka;

import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.event.ForexConversionRequestedEvent;
import com.capstone.forex.service.ForexConversionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes FOREX_CONVERSION_REQUESTED events from Kafka and invokes
 * ForexConversionService to calculate conversion and stage FOREX_CONVERSION_COMPLETED (FC-45).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ForexEventConsumer {

    private final ForexConversionService forexConversionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.FOREX_CONVERSION_REQUESTED,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(Object payload) {
        if (payload == null) {
            return;
        }

        try {
            Object raw = payload instanceof org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> cr ? cr.value() : payload;
            ForexConversionRequestedEvent request;
            if (raw instanceof ForexConversionRequestedEvent typed) {
                request = typed;
            } else if (raw instanceof String s) {
                request = objectMapper.readValue(s, ForexConversionRequestedEvent.class);
            } else {
                request = objectMapper.convertValue(raw, ForexConversionRequestedEvent.class);
            }

            log.info("Received FOREX_CONVERSION_REQUESTED for txnId={}: amount={} {} -> {}",
                    request.txnId(), request.sourceAmount(), request.sourceCurrency(), request.destCurrency());

            forexConversionService.processConversion(request);

        } catch (Exception ex) {
            log.error("Failed to process FOREX_CONVERSION_REQUESTED event payload: {}", payload, ex);
        }
    }
}
