package com.railway.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("refund_transactions")
public class RefundTransactionDocument {
    @Id public String id;
    public String reference, stripeRefundId, reason;   // reference = PNR or food orderId
    public double amount;
    public Instant createdAt = Instant.now();
}
