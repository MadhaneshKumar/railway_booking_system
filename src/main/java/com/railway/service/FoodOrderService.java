package com.railway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railway.document.BookingDocument;
import com.railway.document.FoodMenuDocument;
import com.railway.document.FoodMenuDocument.MenuItem;
import com.railway.document.FoodOrderDocument;
import com.railway.repository.Repos.*;
import com.stripe.model.PaymentIntent;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;

@Service
public class FoodOrderService {
    public record OrderResult(FoodOrderDocument order, String clientSecret) {}

    private final FoodMenuRepo menus; private final FoodOrderRepo orders; private final BookingService bookingService;
    private final PaymentService payments; private final PdfTicketService pdf; private final BlobStorageService blobs;
    private final NotificationService notifier; private final StringRedisTemplate redis; private final ObjectMapper mapper;
    private final MongoTemplate mongo;

    public FoodOrderService(FoodMenuRepo menus, FoodOrderRepo orders, BookingService bookingService, PaymentService payments,
                            PdfTicketService pdf, BlobStorageService blobs, NotificationService notifier,
                            StringRedisTemplate redis, ObjectMapper mapper, MongoTemplate mongo) {
        this.menus = menus; this.orders = orders; this.bookingService = bookingService; this.payments = payments; this.pdf = pdf;
        this.blobs = blobs; this.notifier = notifier; this.redis = redis; this.mapper = mapper; this.mongo = mongo;
    }

    /** Menu cached in Redis for 30 minutes. */
    public FoodMenuDocument menu(String station) {
        String key = "menu:" + station;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) return mapper.readValue(cached, FoodMenuDocument.class);
            FoodMenuDocument m = menus.findByStationCode(station)
                    .orElseThrow(() -> new BookingException("MENU_NOT_FOUND", "No menu for station " + station));
            redis.opsForValue().set(key, mapper.writeValueAsString(m), Duration.ofMinutes(30));
            return m;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new RuntimeException(e); }
    }

    public OrderResult order(String pnr, String station, Map<String, Integer> items) {
        BookingDocument b = bookingService.require(pnr);
        if (!"CONFIRMED".equals(b.bookingStatus)) throw new BookingException("PNR_NOT_CONFIRMED", "Food can be ordered only for confirmed tickets");
        FoodMenuDocument menu = menu(station);
        FoodOrderDocument o = new FoodOrderDocument();
        o.orderId = "F" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        o.pnr = pnr; o.userId = b.userId; o.stationCode = station; o.status = "PENDING_PAYMENT";
        for (var e : items.entrySet()) {
            MenuItem mi = menu.items.stream().filter(m -> m.itemId.equals(e.getKey())).findFirst()
                    .orElseThrow(() -> new BookingException("ITEM_NOT_FOUND", "Item " + e.getKey() + " not on menu"));
            FoodOrderDocument.Line l = new FoodOrderDocument.Line();
            l.itemId = mi.itemId; l.name = mi.name; l.qty = e.getValue(); l.price = mi.price;
            o.lines.add(l); o.totalAmount += mi.price * e.getValue();
        }
        PaymentIntent pi = payments.createFoodIntent(o);
        o.paymentIntentId = pi.getId();
        orders.save(o);
        return new OrderResult(o, pi.getClientSecret());
    }

    /** Stripe webhook: PENDING_PAYMENT -> PLACED (idempotent), then vendor + customer notification and invoice. */
    public void confirmPaid(String orderId) {
        FoodOrderDocument o = mongo.findAndModify(
                Query.query(Criteria.where("orderId").is(orderId).and("status").is("PENDING_PAYMENT")),
                new Update().set("status", "PLACED").set("estimatedDelivery", LocalTime.now().plusMinutes(45).withNano(0).toString()),
                FindAndModifyOptions.options().returnNew(true), FoodOrderDocument.class);
        if (o == null) return;
        try {
            String blob = "invoices/" + o.orderId + ".pdf";
            blobs.upload(blob, pdf.invoice(o));
            mongo.updateFirst(Query.query(Criteria.where("orderId").is(orderId)), new Update().set("invoiceBlob", blob), FoodOrderDocument.class);
        } catch (Exception ignored) { }
        // Vendor notification: log it (a real vendor webhook / queue would be called here)
        org.slf4j.LoggerFactory.getLogger(getClass()).info("VENDOR NOTIFIED: order {} for station {}", o.orderId, o.stationCode);
        notifier.notify(o.userId, "Food order confirmed", "Order " + o.orderId + " at " + o.stationCode + ", ETA " + o.estimatedDelivery);
    }

    public FoodOrderDocument status(String orderId) {
        return orders.findByOrderId(orderId).orElseThrow(() -> new BookingException("ORDER_NOT_FOUND", "Unknown order"));
    }

    public Object[] cancel(String orderId) {
        FoodOrderDocument o = mongo.findAndModify(
                Query.query(Criteria.where("orderId").is(orderId).and("status").in("PENDING_PAYMENT", "PLACED")),
                new Update().set("status", "CANCELLED"), FindAndModifyOptions.options().returnNew(false), FoodOrderDocument.class);
        if (o == null) throw new BookingException("NOT_CANCELLABLE", "Order not found or already delivered/cancelled");
        double refund = 0;
        if ("PLACED".equals(o.status)) { payments.refund(o.paymentIntentId, o.totalAmount); refund = o.totalAmount; }
        else payments.cancelIntent(o.paymentIntentId);
        notifier.notify(o.userId, "Food order cancelled", "Order " + orderId + " cancelled. Refund INR " + refund);
        return new Object[]{refund, "CANCELLED"};
    }
}
