package com.railway.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("notification_logs")
public class NotificationLogDocument {
    @Id public String id;
    public String userId, channel, recipient, subject, message, status;   // status: SENT | MOCK | FAILED
    public Instant createdAt = Instant.now();
}
