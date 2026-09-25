package com.capstone.ledger.frontend.model;

import java.time.LocalDateTime;

/**
 * Frontend view model for alerts/notifications matching NOTIFICATION_AUDIT in the ERD.
 */
public class NotificationView {

    private String id;
    private String custId;
    private String message;
    private String channel; // SMS, EMAIL, PUSH
    private String status; // PENDING, SENT, FAILED
    private LocalDateTime sentAt;
    private boolean read;

    public NotificationView() {
        this.status = "SENT";
        this.channel = "SMS";
    }

    public NotificationView(String id, String custId, String message, LocalDateTime sentAt, boolean read) {
        this(id, custId, message, "SMS", "SENT", sentAt, read);
    }

    public NotificationView(String id, String custId, String message, String channel,
                            String status, LocalDateTime sentAt, boolean read) {
        this.id = id;
        this.custId = custId;
        this.message = message;
        this.channel = channel != null ? channel : "SMS";
        this.status = status != null ? status : "SENT";
        this.sentAt = sentAt != null ? sentAt : LocalDateTime.now();
        this.read = read;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustId() { return custId; }
    public void setCustId(String custId) { this.custId = custId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
