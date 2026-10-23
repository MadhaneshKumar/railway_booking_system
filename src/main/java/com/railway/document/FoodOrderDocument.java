package com.railway.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.*;

@Document("food_orders")
public class FoodOrderDocument {
    @Id public String id;
    @Indexed(unique = true) public String orderId;
    public String pnr, userId, stationCode, estimatedDelivery, paymentIntentId, invoiceBlob;
    /** PENDING_PAYMENT | PLACED | DELIVERED | CANCELLED */
    public String status;
    public double totalAmount;
    public List<Line> lines = new ArrayList<>();
    public Instant createdAt = Instant.now();
    public static class Line { public String itemId, name; public int qty; public double price; }
}
