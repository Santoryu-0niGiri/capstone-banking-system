package com.capstone.notification.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationDto(
        UUID notifId,
        String customerId,
        String message,
        String status,
        OffsetDateTime createdAt
) {
}
