package com.capstone.notification.controller;

import com.capstone.common.dto.ApiResponse;
import com.capstone.notification.dto.NotificationDto;
import com.capstone.notification.entity.NotificationAudit;
import com.capstone.notification.repository.NotificationAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationAuditRepository auditRepository;

    @GetMapping("/{customerId}")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getNotifications(
            @PathVariable("customerId") String customerId,
            @RequestParam(value = "accountIds", required = false) List<String> accountIds) {

        List<NotificationAudit> audits;
        if (accountIds != null && !accountIds.isEmpty()) {
            audits = auditRepository.findByCustomerIdOrAccountIds(customerId, accountIds);
        } else {
            audits = auditRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        }

        List<NotificationDto> dtos = audits.stream()
                .map(a -> new NotificationDto(
                        a.getNotifId(),
                        a.getCustomerId(),
                        a.getMessage(),
                        a.getStatus(),
                        a.getCreatedAt()))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable("id") UUID id) {
        auditRepository.findById(id).ifPresent(audit -> {
            audit.setStatus("READ");
            auditRepository.save(audit);
        });
        return ResponseEntity.ok(ApiResponse.ok("Notification marked as read", null));
    }
}
