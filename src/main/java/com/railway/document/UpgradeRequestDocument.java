package com.railway.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("upgrade_requests")
public class UpgradeRequestDocument {
    @Id public String id;
    @Indexed(unique = true) public String requestId;
    public String pnr, preferredClass, upgradedClass;
    /** UPGRADED | PENDING */
    public String status;
    public Instant createdAt = Instant.now();
}
