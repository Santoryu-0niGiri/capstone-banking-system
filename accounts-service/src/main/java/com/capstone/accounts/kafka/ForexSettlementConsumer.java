package com.capstone.accounts.kafka;

import com.capstone.accounts.service.AccountService;
import com.capstone.common.constants.KafkaTopics;
import com.capstone.common.event.ForexConversionCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes forex.conversion.completed events (published by ForEx Service outbox relay).
 * Triggers row-locked balance mutation across source and destination accounts (FC-47).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ForexSettlementConsumer {

    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.FOREX_CONVERSION_COMPLETED,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(Object payload) {
        if (payload == null) {
            return;
        }

        try {
            ForexConversionCompletedEvent event;
            if (payload instanceof ForexConversionCompletedEvent typed) {
                event = typed;
            } else if (payload instanceof String s) {
                event = objectMapper.readValue(s, ForexConversionCompletedEvent.class);
            } else {
                event = objectMapper.convertValue(payload, ForexConversionCompletedEvent.class);
            }

            log.info("Received FOREX_CONVERSION_COMPLETED for txnId={}: src={} dest={} srcAmt={} destAmt={}",
                    event.txnId(), event.sourceAccountId(), event.destAccountId(), event.sourceAmount(), event.destAmount());

            accountService.settleCrossCurrency(event);

        } catch (Exception ex) {
            log.error("Failed to process FOREX_CONVERSION_COMPLETED event: {}", payload, ex);
        }
    }
}
